package cn.sinjinsong.chat.server.reverse.router.core.client;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

@Slf4j
public class ServiceClient {
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private Selector selector;
    private SocketChannel clientChannel;
    private ByteBuffer buf;
    private ReceiverHandler listener;
    private static final int PORT = 10665;

    public static void main(String[] args) {
        ServiceClient serviceClient = new ServiceClient();
        serviceClient.initNetWork();
        serviceClient.launch();
    }

    public void launch() {
        this.listener = new ReceiverHandler();
        new Thread(listener).start();
    }

    private void initNetWork() {
        try {
            selector = Selector.open();
            clientChannel = SocketChannel.open(new InetSocketAddress("127.0.0.1", PORT));
            //设置客户端为非阻塞模式
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            buf = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
            clientChannel.write(ByteBuffer.wrap("demoserver".getBytes()));
        } catch (ConnectException e) {
            log.info("error", e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ReceiverHandler implements Runnable {
        private boolean connected = true;

        public void shutdown() {
            connected = false;
        }


        public void run() {
            try {
                while (connected) {
                    int size = 0;
                    selector.select();
                    for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                        SelectionKey selectionKey = it.next();
                        it.remove();
                        if (selectionKey.isReadable()) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            while ((size = clientChannel.read(buf)) > 0) {
                                buf.flip();
                                baos.write(buf.array(), 0, size);
                                buf.clear();
                            }
                            byte[] bytes = baos.toByteArray();
                            baos.close();
                            log.info(new String(bytes));
                        }
                    }
                }
            } catch (IOException e) {
                log.error("error",e);
            }
        }

    }

}
