package cn.sinjinsong.chat.server.reverse.router.nio.handler;

import com.alibaba.fastjson.JSON;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author hufangjie
 * @date 2019-08-29 18:42
 */
public class ProcessHandler {

    private ThreadPoolExecutor threadPoolExecutor;

    public void init(){
        threadPoolExecutor =  new ThreadPoolExecutor(10, 20, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }


    public void submit(SelectionKey key) throws IOException {
        System.out.println("数据读取事件准备好");
        SocketChannel sc = (SocketChannel) key.channel();

        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        int readBytes = sc.read(readBuffer);
        System.out.println("The time server receive order : ");
        while (readBytes > 0){
            readBuffer.flip();
            byte[] bytes = new byte[readBuffer.remaining()];
            readBuffer.get(bytes);
            String body = new String(bytes, "UTF-8");
            System.out.print(body);
            readBytes = sc.read(readBuffer);
        }
        String currentTime = new Date(System.currentTimeMillis()).toString();
        String header = "HTTP/1.1 200 \n" +
                "Server: nginx/1.12.2\n" +
                "Date: Thu, 29 Aug 2019 07:58:45 GMT\n" +
                "Content-Type: application/json;charset=UTF-8\n" +
                "Content-Length: %d\n" +
                "Connection: Keep-alive\n" +
                "\n";

        Response response = new Response();
        response.setCode(8000);
        response.setSuccess(true);
        response.setTime(currentTime);

        String httpBody = JSON.toJSONString(response);
        header = String.format(header, httpBody.length());

        String result = header + httpBody;
        //一次响应
        doWrite(sc, result);
        key.cancel();
        sc.close();
    }

    private void doWrite(SocketChannel channel, String response) throws  IOException{
        if (response != null && response.trim().length() > 0){
            byte[] bytes = response.getBytes();
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            writeBuffer.put(bytes);
            writeBuffer.flip();
            channel.write(writeBuffer);
        }
    }

    private static class Response{
        private int code;
        private boolean success;
        private String time;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getTime() {
            return time;
        }

        public void setTime(String time) {
            this.time = time;
        }
    }
}
