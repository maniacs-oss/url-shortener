package controllers;

import org.apache.commons.lang.RandomStringUtils;

import com.sun.jndi.toolkit.url.UrlUtil;
import play.*;
import play.mvc.*;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Application extends Controller {

   static final JedisShardInfo redisConfig = new JedisShardInfo(
           Play.configuration.getProperty("redis.host", "localhost"),
           Integer.valueOf(Play.configuration.getProperty("redis.port", "6379")));

   static {
      redisConfig.setPassword(Play.configuration.getProperty("redis.password", "foobared"));
   }

   public static void index() {
      render();
   }

   public static void getUrl(String key) {
      Jedis jedis = new Jedis(redisConfig);
      String redirectUrl = jedis.get("fromkey:" + key);
      if (redirectUrl == null) {
         notFound();
      }
      jedis.incr("count:" + key);
      redirect(redirectUrl);
   }

   private static String postUrl(String url, Jedis jedis) {
      Set<String> oldKeys = jedis.keys("fromurl:" + url);
      if (oldKeys.isEmpty()) {
         String key = generateKey(jedis);
         String niceUrl = url.startsWith("http://") || url.startsWith("https://") ? url : "http://" + url;
         jedis.set("fromkey:" + key, niceUrl);
         jedis.set("fromurl:" + niceUrl, key);
         jedis.set("count:" + key, "0");
         return key;
      } else {
         return jedis.get(oldKeys.iterator().next());
      }
   }

   public static String postUrl(String url) {
      Jedis jedis = new Jedis(redisConfig);

      return postUrl(url, jedis);
   }

   private static String generateKey(Jedis jedis) {
      String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ123456789";
      int size = 1;
      String key = null, exitingUrl = null;
      do {
         key = RandomStringUtils.random(size, letters);
         exitingUrl = findUrl(key, jedis);
         size++;
      } while (exitingUrl != null);
      return key;
   }

   public static void list(String p) {
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys(p);
      if (!keys.isEmpty()) {
         List<String> values = jedis.mget(keys.toArray(new String[keys.size()]));
         renderJSON(new HashSet<String>(values));
      } else {
         notFound();
      }
   }

   public static void migrate() {
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys("url#*");
      for (String oldkey : keys) {
         String url = jedis.get(oldkey);
         postUrl(url);
         jedis.del(oldkey);
      }
      renderText("OK");
   }

   public static Integer count() {
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys("fromkey:*");
      return keys.size();
   }

   public static void clean() {
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys("fromkey:*");
      List<String> deletedUrl = new ArrayList<String>();
      for (String key : keys) {
         try {
            URL url = new URL(jedis.get(key));
            url.openStream().close();
         } catch (Throwable e) {
            deletedUrl.add(jedis.get(key));
            jedis.del(key);
         }
      }
      renderJSON(deletedUrl);
   }

   private static String findUrl(String key,Jedis jedis) {
      return jedis.get("fromkey:" + key);
   }
}