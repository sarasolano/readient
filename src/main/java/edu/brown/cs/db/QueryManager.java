package edu.brown.cs.db;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.codec.binary.Base64;

/**
 * A QueryManager for a database with the following schema.
 *
 * @author sarasolano
 */
public class QueryManager implements AutoCloseable {
  /**
   * The secure random number generator for salts.
   */
  private static final Random RANDOM = new SecureRandom();
  /**
   * The size of the salt.
   */
  private static final int SALT_SIZE = 64;
  /**
   * The amount of iterations for the hashing.
   */
  private static final int ITERATIONS = 10000;
  /**
   * The length of the hash key.
   */
  private static final int KEY_LENGTH = 256;
  /**
   * The database connection.
   */
  private Connection conn;

  /**
   * Constructs a query manager.
   *
   * @param db
   *          the path to the database
   * @throws SQLException
   *           if there is a database access error
   * @throws ClassNotFoundException
   *           if the class is not found
   */
  public QueryManager(String db) throws SQLException, ClassNotFoundException {
    Class.forName("org.sqlite.JDBC");
    conn = DriverManager.getConnection("jdbc:sqlite:" + db);
  }

  /**
   * Adds a user to the database.
   *
   * @param username
   *          the user name
   * @param password
   *          the password of the profile
   * @param fName
   *          the first name
   * @param lName
   *          the last name
   * @throws SQLException
   *           if there is an error while executing
   */
  public void addUser(String username, String password, String fName,
      String lName) throws SQLException {
    String query = "INSERT INTO user VALUES(?, ?, ?, ?, ?)";
    PreparedStatement stat = conn.prepareStatement(query);
    byte[] salt = getSalt();
    stat.setString(1, fName);
    stat.setString(2, lName);
    stat.setString(3, username);
    stat.setString(4, bytetoString(hash(password, salt)));
    stat.setString(5, bytetoString(salt));
    stat.execute();
    stat.close();
  }

  /**
   * Adds an article to the database.
   *
   * @param name
   *          the name of the article
   * @param username
   *          the user that submitted the article
   * @throws SQLException
   *           if there is an error while executing
   */
  public void addArticle(String name, String username) throws SQLException {
    String query = "INSERT INTO article VALUES(?, ?, ?)";
    PreparedStatement stat = conn.prepareStatement(query);
    stat.setString(1, "NULL");
    stat.setString(2, name);
    stat.setString(3, username);
    stat.execute();
    stat.close();
  }

  /**
   * Add article's reading level to the database.
   *
   * @param artID
   *          the id of the article
   * @param readLevel
   *          the reading level of the article
   * @throws SQLException
   *           if there is an error while executing
   */
  public void addReadLevel(String artID, double readLevel) throws SQLException {
    String query = "INSERT INTO read_level VALUES(?, ?)";
    PreparedStatement stat = conn.prepareStatement(query);
    stat.setString(1, artID);
    stat.setDouble(2, readLevel);
    stat.execute();
    stat.close();
  }

  /**
   * Adds article sentiment probabilities to the database.
   *
   * @param artID
   *          the id of the article
   * @param posProb
   *          the positive probability
   * @param negProb
   *          the negative probability
   * @throws SQLException
   *           if there is a problem while executing
   */
  public void addSentiment(String artID, double posProb, double negProb)
      throws SQLException {
    String query = "INSERT INTO sentiment VALUES(?, ?, ?)";
    PreparedStatement stat = conn.prepareStatement(query);
    for (int i = 0; i < 2; i++) {
      stat.setInt(1, i);
      stat.setString(2, artID);
      stat.setDouble(3, i == 0 ? negProb : posProb);
      stat.addBatch();
    }
    stat.execute();
    stat.close();
  }

  /**
   * Adds a topic to the database.
   *
   * @param artID
   *          the id of the article.
   * @param topic
   *          the topic of the article
   * @throws SQLException
   *           if there is an error while executing
   */
  public void addTopic(String artID, String topic) throws SQLException {
    String query = "INSERT INTO topic VALUES(?, ?)";
    PreparedStatement stat = conn.prepareStatement(query);
    stat.setString(1, artID);
    stat.setString(2, topic);
    stat.execute();
    stat.close();
  }

  /**
   * Adds a topic to the database.
   *
   * @param artID
   *          the id of the article.
   * @param topics
   *          the set of topic of the article
   * @throws SQLException
   *           if there is an error while executing
   */
  public void addTopics(String artID, Set<String> topics) throws SQLException {
    String query = "INSERT INTO topic VALUES(?, ?)";
    PreparedStatement stat = conn.prepareStatement(query);
    for (String topic : topics) {
      stat.setString(1, artID);
      stat.setString(2, topic);
      stat.addBatch();
    }
    stat.execute();
    stat.close();
  }

  /**
   * Adds a mood to the database.
   *
   * @param artID
   *          the id of the article
   * @param mood
   *          the mood
   * @param prob
   *          the mood probability
   * @throws SQLException
   *           if there is an error while executing
   */
  public void addMood(String artID, String mood, double prob)
      throws SQLException {
    String query = "INSERT INTO mood VALUES(?, ?, ?)";
    PreparedStatement stat = conn.prepareStatement(query);
    stat.setString(1, artID);
    stat.setString(2, mood);
    stat.setDouble(3, prob);
    stat.execute();
    stat.close();
  }

  /**
   * Adds a mood to the database.
   *
   * @param artID
   *          the id of the article
   * @param moods
   *          a map from mood to probability
   * @throws SQLException
   *           if there is an error while executing
   */
  public void addMoods(String artID, Map<String, Double> moods)
      throws SQLException {
    String query = "INSERT INTO mood VALUES(?, ?, ?)";
    PreparedStatement stat = conn.prepareStatement(query);
    for (Entry<String, Double> mood : moods.entrySet()) {
      stat.setString(1, artID);
      stat.setString(2, mood.getKey());
      stat.setDouble(3, mood.getValue());
      stat.addBatch();
    }
    stat.executeBatch();
    stat.close();
  }

  /**
   * Gets the avg reading level for a user's profile.
   *
   * @param username
   *          the user name of the user
   * @return the avg reading level of the user
   * @throws SQLException
   *           if there is an error while querying
   */
  public double avgReadLevel(String username) throws SQLException {
    String query = "SELECT SUM(read_level)*100/COUNT(*) FROM "
        + "read_level, article WHERE article.id == read_level.article "
        + "AND article.user == ?";
    PreparedStatement stat = conn.prepareStatement(query);
    stat.setString(1, username);
    ResultSet results = stat.executeQuery();
    // only add if results isn't empty
    double toReturn = 0;
    if (results.next()) {
      toReturn = results.getDouble(1);
    }
    stat.close();
    results.close();
    return toReturn;
  }

  /**
   * Returns a salted and hashed password using the provided hash.
   *
   * @param password
   *          the password to be hashed
   * @param salt
   *          a 16 bytes salt, ideally obtained with the getNextSalt method
   * @return the hashed password with a pinch of salt
   */
  public static byte[] hash(String password, byte[] salt) {
    char[] pwd = password.toCharArray();
    PBEKeySpec spec = new PBEKeySpec(pwd, salt, ITERATIONS, KEY_LENGTH);
    Arrays.fill(pwd, Character.MIN_VALUE);
    try {
      SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      return skf.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new AssertionError(
          "Error while hashing a password: " + e.getMessage(), e);
    } finally {
      spec.clearPassword();
    }
  }

  /**
   * Returns true if the given password and salt match the hashed value, false
   * otherwise.
   *
   * @param password
   *          the password to check
   * @param salt
   *          the salt used to hash the password
   * @param expectedHash
   *          the expected hashed value of the password
   * @return true if the given password and salt match the hashed value, false
   *         otherwise
   */
  public static boolean isExpectedPassword(String password, byte[] salt,
      byte[] expectedHash) {
    char[] pwd = password.toCharArray();
    byte[] pwdHash = hash(password, salt);
    Arrays.fill(pwd, Character.MIN_VALUE);
    if (pwdHash.length != expectedHash.length) {
      return false;
    }
    for (int i = 0; i < pwdHash.length; i++) {
      if (pwdHash[i] != expectedHash[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns a random salt to be used to hash a password.
   *
   * @return a 64 bytes random salt
   */
  public static byte[] getSalt() {
    byte[] salt = new byte[SALT_SIZE];
    RANDOM.nextBytes(salt);
    return salt;
  }

  /**
   * Converts a base 64 byte array into a String.
   *
   * @param input
   *          the input to convert
   * @return the byte array converted into a string
   */
  public static String bytetoString(byte[] input) {
    return Base64.encodeBase64String(input);
  }

  /**
   * Converts the given String into a byte array of base 64.
   *
   * @param input
   *          the input to convert
   * @return the string converted into a byte array
   */
  public static byte[] stringToByte(String input) {
    if (Base64.isBase64(input)) {
      return Base64.decodeBase64(input);

    } else {
      return Base64.encodeBase64(input.getBytes());
    }
  }

  @Override
  public void close() throws Exception {
    conn.close();
  }

}
