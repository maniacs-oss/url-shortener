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
      String redirectUrl = jedis.get("url#" + key);
      if (redirectUrl == null) {
         notFound();
      }
      redirect(redirectUrl);
   }

   public static String postUrl(String url) {
      Jedis jedis = new Jedis(redisConfig);
      String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ123456789";
      int size = 1;
      String key = null, exitingUrl = null;
      do {
         key = RandomStringUtils.random(size, letters);
         exitingUrl = findUrl(key);
         size++;
      } while (exitingUrl != null);

      String niceUrl = url.startsWith("http://") || url.startsWith("https://") ? url : "http://" + url;
      jedis.set("url#" + key, niceUrl);
      return key;
   }

   public static void list(String p) {
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys(p);
      List<String> values = jedis.mget(keys.toArray(new String[keys.size()]));
      renderJSON(new HashSet<String>(values));
   }

   public static Integer count() {
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys("url#*");
      return keys.size();
   }

   public static void clean(){
      Jedis jedis = new Jedis(redisConfig);
      Set<String> keys = jedis.keys("url#*");
      List<String> deletedUrl = new ArrayList<String>();
      for(String key : keys){
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

   private static String findUrl(String key) {
      Jedis jedis = new Jedis(redisConfig);
      return jedis.get("url#" + key);
   }
}