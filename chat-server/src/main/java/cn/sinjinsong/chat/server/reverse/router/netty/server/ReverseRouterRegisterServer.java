package cn.sinjinsong.chat.server.reverse.router.netty.server;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ReverseRouterRegisterServer {
    private static final int PORT = 10665;
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private Map<String, SocketChannel> proxyServerTable;

    private ListenerThread listenerThread;

    private void initServer() {
        try {
            proxyServerTable = new ConcurrentHashMap();
            serverSocketChannel = ServerSocketChannel.open();
            //切换为非阻塞模式
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(PORT));
            //获得选择器
            selector = Selector.open();
            //将channel注册到selector上
            //第二个参数是选择键，用于说明selector监控channel的状态
            //可能的取值：SelectionKey.OP_READ OP_WRITE OP_CONNECT OP_ACCEPT
            //监控的是channel的接收状态
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            this.listenerThread = new ListenerThread();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 推荐的结束线程的方式是使用中断
     * 在while循环开始处检查是否中断，并提供一个方法来将自己中断
     * 不要在外部将线程中断
     * <p>
     * 另外，如果要中断一个阻塞在某个地方的线程，最好是继承自Thread，先关闭所依赖的资源，再关闭当前线程
     */
    private class ListenerThread extends Thread {

        @Override
        public void interrupt() {
            try {
                try {
                    selector.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } finally {
                super.interrupt();
            }
        }

        @Override
        public void run() {
            try {
                //如果有一个及以上的客户端的数据准备就绪
                while (!Thread.currentThread().isInterrupted()) {
                    //当注册的事件到达时，方法返回；否则,该方法会一直阻塞
                    selector.select();
                    //获取当前选择器中所有注册的监听事件
                    for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                        SelectionKey key = it.next();
                        //删除已选的key,以防重复处理
                        it.remove();
                        //如果"接收"事件已就绪
                        if (key.isAcceptable()) {
                            //交由接收事件的处理器处理
                            handleAcceptRequest();
                        } else if (key.isReadable()) {
                            //如果"读取"事件已就绪
                            //取消可读触发标记，本次处理完后才打开读取事件标记
                            key.interestOps(key.interestOps() & (~SelectionKey.OP_READ));
                            //注册服务器
                            SocketChannel socketChannel = (SocketChannel) key.channel();
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            int size;
                            ByteBuffer buf = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
                            while ((size = socketChannel.read(buf)) > 0) {
                                buf.flip();
                                baos.write(buf.array(), 0, size);
                                buf.clear();
                            }

                            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                            key.selector().wakeup();

                            byte[] bytes = baos.toByteArray();
                            baos.close();
                            String serverName = new String(bytes);

                            String routeKey = socketChannel.getLocalAddress().toString() + "_" + serverName;
                            log.info(routeKey + " register");
                            proxyServerTable.put(routeKey, socketChannel);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void shutdown() {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 处理客户端的连接请求
     */
    private void handleAcceptRequest() {
        try {
            SocketChannel client = serverSocketChannel.accept();
            // 接收的客户端也要切换为非阻塞模式
            client.configureBlocking(false);
            // 监控客户端的读操作是否就绪
            client.register(selector, SelectionKey.OP_READ);
            log.info("服务器连接客户端:{}",client);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 启动方法，线程最好不要在构造函数中启动，应该作为一个单独方法，或者使用工厂方法来创建实例
     * 避免构造未完成就使用成员变量
     */
    public void launch() {
        new Thread(listenerThread).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    try {
                        Thread.sleep(1000);

                        log.info("hear start");
                        for (Map.Entry<String, SocketChannel> entry : proxyServerTable.entrySet()){
                            SocketChannel socketChannel = entry.getValue();
                            if (socketChannel.isConnected()){
                                try {
                                    socketChannel.write(ByteBuffer.wrap("heart".getBytes()));
                                    log.info(entry.getKey() + " success");
                                }catch (Exception e){
                                    log.error("error", e);
                                    socketChannel.close();
                                    proxyServerTable.remove(entry.getKey());
                                }
                            }else {
                                log.info(entry.getKey() + " failed");
                                proxyServerTable.remove(entry.getKey());
                            }
                        }

                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                }

            }

        }).start();
    }

    public static void main(String[] args) {
        ReverseRouterRegisterServer reverseRouterRegisterServer = new ReverseRouterRegisterServer();

        reverseRouterRegisterServer.initServer();
        log.info("启动服务");
        reverseRouterRegisterServer.launch();
        log.info("启动注册监听器");
    }
}
