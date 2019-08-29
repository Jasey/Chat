package cn.sinjinsong.chat.server.reverse.router.nio;

import cn.sinjinsong.chat.server.reverse.router.nio.server.MultiplexerTimeServer;

/**
 * @author hufangjie
 * @date 2019-08-29 18:35
 */
public class HttpServerTest {
        public static void main(String[] args){
            int port = 8080;
            if (args != null && args.length > 0){
                try {
                    port = Integer.valueOf(args[0]);
                }catch (NumberFormatException e){

                }
            }
            MultiplexerTimeServer timeServer = new MultiplexerTimeServer(port);
            new Thread(timeServer, "NIO-MultiplexerTimeServer-001").start();
        }
}
