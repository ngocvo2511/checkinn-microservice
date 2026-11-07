import java.net.*;
import java.io.*;

/** Temporary loopback-published transport for host tests into checkinn-test only. No payload logging. */
public class TestTransportForward {
    static void copy(Socket from,Socket to) {
        try {from.getInputStream().transferTo(to.getOutputStream());}
        catch(IOException ignored) {}
        finally {try{from.close();}catch(IOException ignored){}try{to.close();}catch(IOException ignored){}}
    }
    static void listen(int port,String host,int target) {
        try(ServerSocket server=new ServerSocket(port)) {
            for(;;) {
                Socket from=server.accept();
                Thread.startVirtualThread(()->{
                    try {Socket to=new Socket(host,target);Thread.startVirtualThread(()->copy(to,from));copy(from,to);}
                    catch(IOException ignored){try{from.close();}catch(IOException ignoredAgain){}}
                });
            }
        }catch(IOException ex){throw new IllegalStateException("Test transport could not start");}
    }
    public static void main(String[] args)throws InterruptedException {
        new Thread(()->listen(16379,"redis",6379)).start();
        new Thread(()->listen(15432,"postgres-test",5432)).start();
        new java.util.concurrent.CountDownLatch(1).await();
    }
}
