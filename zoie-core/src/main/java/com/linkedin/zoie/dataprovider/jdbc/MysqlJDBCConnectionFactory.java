package com.linkedin.zoie.dataprovider.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.linkedin.zoie.dataprovider.jdbc.JDBCConnectionFactory;

public class MysqlJDBCConnectionFactory implements JDBCConnectionFactory {
	private static final String MYSQL_JDBC_URL_PREFIX="jdbc:mysql://";
	private static final String MYSQL_DRIVER_NAME = "com.mysql.jdbc.Driver";
	
	private final String _username;
	private final String _pw;
	private final String _url;
	
	private Connection _conn = null;
	
	public MysqlJDBCConnectionFactory(String url,String username,String password){
		_url = MYSQL_JDBC_URL_PREFIX+url;
		_username = username;
		_pw = password;
	}
	
	public synchronized Connection getConnection() throws SQLException {
		if (_conn == null){
	 	  try {
			Class.forName (MYSQL_DRIVER_NAME).newInstance ();
		  } catch (Exception e) {
			throw new SQLException("unable to load driver: "+e.getMessage());
		  }
          _conn = DriverManager.getConnection (_url, _username, _pw);
		}
		return _conn;
	}
	
	public synchronized void showndown() throws SQLException{
		if (_conn!=null){
			_conn.close();
		}
	}
}
