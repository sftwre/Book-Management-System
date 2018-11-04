package Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import Model.AuditTrailEntry;
import Model.Book;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Sep 17, 2018
 * isaacbuitrago
 * 
 * Manages interaction between the 
 * Book model and the Book table in the database.
 */
public class BookTableGateway 
{
	private Connection conn;		// connection to data store
	
	private PreparedStatement stmt;	// statement to execute

	private ResultSet result;		// result returned from MySQL
	
	private String sql;				// SQL statement to execute against the database
	
	
	/**
	 * Constructor with specified connection
	 * @param conn
	 */
	public BookTableGateway(Connection conn)
	{
		this.conn = conn;
	}
	
	/**
	 * Constructor with default connection
	 * @throws SQLException if connection could not be established 
	 */
	public BookTableGateway() throws SQLException
	{
		this.conn = DBConnection.getInstance().getConnection();
	}
	
	/**
	 * Used to retrieve all books in the database
	 * @return List of Books from the database
	 * @throws SQLException if an error occurred while interacting with the database
	 */
	public List<Book> getBooks() throws SQLException
	{
		sql = "select * from Books";
		
		ArrayList<Book> books = new ArrayList<Book>();
		
		stmt = conn.prepareStatement(sql);
		
		result = stmt.executeQuery();
		
		while(result.next())
		{
			Book book = new Book();
			
			book.setId(result.getInt("id"));
			
			book.setTitle(result.getString("title"));
			
			book.setSummary(result.getString("summary"));
			
			book.setYearPublished(result.getInt("year_published"));
			
			book.setIsbn(result.getString("isbn"));
			
			LocalDateTime dateAdded = result.getTimestamp("date_added").toLocalDateTime();
			
			LocalDateTime lastModified = result.getTimestamp("last_modified").toLocalDateTime();
			
			book.setDateAdded(dateAdded);
			
			book.setLastModified(lastModified);
			
			books.add(book);	
		}
		
		return (books);
	}
	
	/**
	 * Used to update a book in the database through the model save function.
	 * Creates a query request for the DB by getting the ID and updates the requested fields with the book object information.
	 * It is assumed that an update is executed in a transaction.
	 * @throws SQLException if an error occurred while interacting with the database
	 */
	public void updateBook(Book book) throws SQLException
	{	
			sql = "UPDATE Books "
	               + "SET title = ? "
	               + ",summary = ? "
	               + ",year_published = ? "
	               + ",isbn = ? "
	               + "WHERE id =" + book.getId();
			
			PreparedStatement preparedStmt = conn.prepareStatement(sql);
			preparedStmt.setString(1, book.getTitle());
			preparedStmt.setString(2, book.getSummary());
			preparedStmt.setInt(3, book.getYearPublished());
			preparedStmt.setString(4, book.getIsbn());
			
			try 
			{	
				preparedStmt.executeUpdate();
				
				// update the last modified time
				book.setLastModified(getBookModifiedTime(book.getId()));
			
			} catch(SQLException e)
			{
				// executing an update in a transaction
				if(! conn.getAutoCommit())
				{
					conn.rollback();
				}
				
				throw e;
			}
	}
	
	/**
	 * Used to lock existing book records in the database by
	 * creating a transaction and selecting the book record for update.
	 * 
	 * @param book record to lock in the database
	 * @throws SQLException  if an error occurred in communicating with the database
	 */
	public void lockBook(Book book) throws SQLException
	{
		sql = "select title from Books where id = ? for update";
		
		conn.setAutoCommit(false);
		
		stmt = conn.prepareStatement(sql);
		
		stmt.setInt(1, book.getId());
		
		stmt.executeQuery();
	}
	
	/**
	 * Turns auto commit back on to end the transaction.
	 * 
	 * It is assumed that auto commit is false when this executes
	 * 
	 * @param book
	 * @throws SQLException 
	 */
	public void unlockBook(Book book) throws SQLException
	{	
		if(! conn.getAutoCommit())
		{
			conn.commit();
			
			conn.setAutoCommit(true);
		}
	}
	
	
	/**
	 * Used to insert a new book into the database
	 * and set the date added timestamp on the book.
	 * If the book already has an id, it is updated.
	 * Returns the id of the new book
	 * 
	 * @param book
	 * @throws SQLException if an error occurred while interacting with the database
	 */
	public int insertBook(Book book) throws SQLException
	{
		ResultSet generatedKeys;	// id of the new book
		
		sql = "insert into Books (title, summary, year_published, publisher_id, isbn) values(?, ?, ?, ?, ?)";
		
		int bookId;
		
		// if the book does not have an id, it should be updated
		if(!(book.getId() == 0))
		{
			updateBook(book);
		}
		
		stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
		
		stmt.setString(1, book.getTitle());
		
		stmt.setString(2, book.getSummary());
		
		stmt.setInt(3, book.getYearPublished());

		stmt.setInt(4, book.getPublisher().getId());
		
		stmt.setString(5, book.getIsbn());
		
		stmt.executeUpdate();
		
		// get the primary key generated for the new book
		generatedKeys = stmt.getGeneratedKeys();
		
		generatedKeys.next();
		
		bookId = generatedKeys.getInt(1);
		
		// set the timestamps for the book
		ArrayList<LocalDateTime> timestamps = getBookTimeStamps(bookId);
		
		book.setDateAdded(timestamps.get(0));
		
		book.setLastModified(timestamps.get(1));
		
		return(bookId);
	}
	
	
	/**
	 * Used to get the date added and last modified timestamps for a book with the given id.
	 * 
	 * @param  bookId  of the book to retrieve the timestamps for
	 * @return ArrayList<LocalDateTime> of timestamps, where the first element is the 
	 *     	   date added timestamp and the second is the last modified timestamp. 
	 * @throws SQLException if a database access error occurs or method called on a closed connection.
	 */
	public ArrayList<LocalDateTime> getBookTimeStamps(int bookId) throws SQLException
	{
		
		ArrayList<LocalDateTime> timestamps = new ArrayList<LocalDateTime>();
		
		sql = "select date_added, last_modified from Books where id = ?";
		
		stmt = conn.prepareStatement(sql);
		
		stmt.setInt(1, bookId);
		
		result = stmt.executeQuery();
		
		result.next();
		
		timestamps.add(result.getTimestamp("date_added").toLocalDateTime());
		
		timestamps.add(result.getTimestamp("last_modified").toLocalDateTime());
		
		return(timestamps);
	}
	
	/**
	 * Used to get the date added timestamp for a book with the given id.
	 * 
	 * @param bookId  of the book to retrieve the date added timestamp
	 * @return LocalDateTime that the book was added to the database, null if the result was empty.
	 * @throws SQLException if a database access error occurs or 
	 * 		   method called on a closed connection.
	 */
	public LocalDateTime getBookModifiedTime(int bookId) throws SQLException
	{
		sql = "SELECT last_modified FROM Books WHERE id = ? ";
		
		stmt = conn.prepareStatement(sql);
		
		stmt.setInt(1, bookId);
		
		result = stmt.executeQuery();
		
		LocalDateTime dateAdded = null;
		
		if(result.next())
		{
			dateAdded = result.getTimestamp("last_modified").toLocalDateTime();
		}
		
		return(dateAdded);
	}
	
	/**
	 * Used to remove a book in the database, sets up a delete query and executes it on the requested field by getting the book object id passed
	 * @return
	 * @throws SQLException
	 */
	public void deleteMethod(Book book) throws SQLException
	{
			sql = "DELETE FROM Books WHERE id = " + book.getId();
			PreparedStatement preparedStmt = conn.prepareStatement(sql);
			preparedStmt.executeUpdate();
	}
	
	public ArrayList<AuditTrailEntry> fetchAuditTrail(Book book) throws SQLException
	{
		sql = "SELECT * FROM book_audit_trail WHERE book_id = " + book.getId(); 
		
		ArrayList<AuditTrailEntry> AtrailEntries = new ArrayList<AuditTrailEntry>();
		
		stmt = conn.prepareStatement(sql);
		
		result = stmt.executeQuery();
		
		while(result.next())
		{

			AuditTrailEntry ATE = new AuditTrailEntry();

			LocalDateTime dateAdded = result.getTimestamp(3).toLocalDateTime();
			
			ATE.setDateAdded(dateAdded);
			
			ATE.setMessage(result.getString(4));
			
			AtrailEntries.add(ATE);	
		}
	
		return AtrailEntries;
	}
	
	public void createNewAuditTrailEntry(Book book,String msg) throws SQLException
	{
		ResultSet generatedKeys;	// id of the new ATE
		
		sql = "insert into book_audit_trail (book_id, entry_msg) values(?, ?)";
		
		// if the book does not have an id, it should be updated
		
		stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
		
		stmt.setInt(1, book.getId());
		
		stmt.setString(2, msg);
		
		stmt.executeUpdate();
		
		generatedKeys = stmt.getGeneratedKeys();
		
		generatedKeys.next();
	}
	
}
