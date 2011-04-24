package controllers;

import org.apache.commons.lang.RandomStringUtils;
import play.*;
import play.cache.Cache;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

    public static void index() {
        render();
    }

    public static void getUrl(String key) {
        String redirectUrl = (String) Cache.get("url#" + key);
        redirect(redirectUrl, true);
    }

    public static String postUrl(String url) {
        String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ123456789";
        int size = 1;
        String key = null, exitingUrl = null;
        do {
            key = RandomStringUtils.random(size, letters);
            exitingUrl = findUrl(key);
            size++;
        }while (exitingUrl != null) ;

        Cache.add("url#" + key, url);
        return key;
    }

    private static String findUrl
            (String
                    key) {
        return (String) Cache.get("url#" + key);
    }

}