package com.linkedin.zoie.dataprovider.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

public interface JDBCConnectionFactory {
	Connection getConnection() throws SQLException;
	void showndown() throws SQLException;
}
