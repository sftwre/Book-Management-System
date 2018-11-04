package Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import Model.Book;
import Model.Publisher;

public class PublisherTableGateway {

	private Connection conn;		// connection to data store
	
	private PreparedStatement stmt;	// statement to execute

	private ResultSet result;		// result returned from MySQL
	
	private String sql;				// SQL statement to execute against the database
	
	public static final int DEFAULT_PUBLISHER_ID = 1;	// Database id of the Unknown publisher 
	
	public PublisherTableGateway(Connection conn)
	{
		this.conn = conn;
	}
	
	public PublisherTableGateway() throws SQLException
	{
		this.conn = DBConnection.getInstance().getConnection();
	}
	
	/**
	 * 
	 * @return
	 * @throws SQLException
	 */
	public ArrayList<Publisher> fetchPublishers() throws SQLException
	{
		sql = "SELECT * FROM Publishers"; 
		
		ArrayList<Publisher> PublisherEntries = new ArrayList<Publisher>();
		
		stmt = conn.prepareStatement(sql);
		
		result = stmt.executeQuery();
		
		while(result.next())
		{

			Publisher publisher = new Publisher();
			
			publisher.setId(result.getInt("id"));
			
			publisher.setName(result.getString("publisher"));
			
			PublisherEntries.add(publisher);	
		}
	
		return PublisherEntries;
	}
	
	public void updatePublisherIDInBooksTable(Book book, int index) throws SQLException
	{
			sql = "UPDATE Books "
	               + "SET publisher_id = ? "
	               + "WHERE id =" + book.getId();
			
			PreparedStatement preparedStmt = conn.prepareStatement(sql);
			preparedStmt.setInt(1, index);
			
			try {
				
				preparedStmt.executeUpdate();
			
			} catch(SQLException e)
			{
				conn.rollback();
				
				throw e;
			} finally {
		
				conn.setAutoCommit(true);
			}
	}
	
}
