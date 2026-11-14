import java.sql.*;
public class Batch5bConnectProbe {
    public static void main(String[] databases) throws Exception {
        Class.forName("org.postgresql.Driver");
        for(String database:databases) {
            if(!database.matches("batch5b_[a-z]+_[0-9]+_[0-9]+|postgres|template1")) throw new IllegalArgumentException("Unowned target");
            try(var ignored=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55486/"+database,System.getenv("BATCH5B_SECURITY_USER"),System.getenv("BATCH5B_SECURITY_PASSWORD"))) {
                System.out.println(database+"|UNEXPECTED_CONNECTED");
            } catch(SQLException expected) {System.out.println(database+"|"+expected.getSQLState());}
        }
    }
}
