package controllers;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.pool.impl.GenericObjectPool.Config;
import play.*;
import play.cache.Cache;
import play.libs.Time;
import play.mvc.*;

import java.util.*;

import models.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class Application extends Controller {

    static final JedisPool pool = new JedisPool(new Config(),
            Play.configuration.getProperty("redis.host", "localhost"),
            Integer.valueOf(Play.configuration.getProperty("redis.port", "6379")),
            300,
            Play.configuration.getProperty("redis.password", "foobared"));

    public static void index() {
        render();
    }

    public static void getUrl(String key) {
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            String redirectUrl = jedis.get("url#" + key);
            if (redirectUrl == null) {
                notFound();
            }
            redirect(redirectUrl);
        } finally {
            if (jedis != null) {
                pool.returnResource(jedis);
            }
        }
    }

    public static String postUrl(String url) {
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ123456789";
            int size = 1;
            String key = null, exitingUrl = null;
            do {
                key = RandomStringUtils.random(size, letters);
                exitingUrl = findUrl(key);
                size++;
            } while (exitingUrl != null);

            String niceUrl = url.startsWith("http://") ? url : "http://" + url;
            jedis.set("url#" + key, niceUrl);
            return key;
        } finally {
            if (jedis != null) {
                pool.returnResource(jedis);
            }
        }
    }

    private static String findUrl(String key) {
        Jedis jedis = null;
        try {
            jedis = pool.getResource();
            return jedis.get("url#" + key);
        } finally {
            if (jedis != null) {
                pool.returnResource(jedis);
            }
        }
    }
}