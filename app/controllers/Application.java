package controllers;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.validator.UrlValidator;

import play.*;
import play.mvc.*;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;

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
      String niceUrl = isValidUrl(url) ? url : "http://" + url;
      Set<String> oldKeys = jedis.keys("fromurl:" + niceUrl);
      if (oldKeys.isEmpty()) {
         if (!isValidUrl(niceUrl)) {
            error("Url is not valid");
         }
         String key = generateKey(jedis);
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
      response.accessControl("*", "POST", true);
      return postUrl(url, jedis);
   }

   public static void optionsPostUrl() {
      response.accessControl("*", "POST", true);
   }

   public static void optionsCount() {
      response.accessControl("*", "GET", true);
   }

   private static boolean isReachableUrl(String url) {
      try {
         URL urlR = new URL(url);
         urlR.openStream().close();
      } catch (Throwable e) {
         return false;
      }
      return true;
   }

   private static boolean isValidUrl(String url) {
      UrlValidator urlValidator = new UrlValidator();
      return urlValidator.isValid(url);
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
         postUrl(url, jedis);
         jedis.del(oldkey);
      }
      renderText("OK");
   }

   public static Integer count() {
      response.accessControl("*", "GET", true);
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys("fromkey:*");
      return keys.size();
   }

   public static void clean() {
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys("fromkey:*");
      List<String> deletedKeys = new ArrayList<String>();
      for (String key : keys) {
         if (!isValidUrl(jedis.get(key)) || !isReachableUrl(jedis.get(key))) {
            deletedKeys.add(jedis.get(key));
            jedis.del("count:" + jedis.get("fromurl:" + jedis.get(key)));
            jedis.del("fromurl:" + jedis.get(key));
            jedis.del(key);
         }
      }
      keys = jedis.keys("fromurl:*");
      for (String key : keys) {
         String urlKey = jedis.get(key);
         if (jedis.keys("fromkey:" + urlKey).isEmpty()) {
            deletedKeys.add(key);
            jedis.del("count:" + urlKey);
            jedis.del("fromkey:" + urlKey);
            jedis.del(key);
         }
      }
      renderJSON(deletedKeys);
   }

   private static String findUrl(String key, Jedis jedis) {
      return jedis.get("fromkey:" + key);
   }
}