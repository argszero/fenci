package com.github.argszero.fenci;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 */
public class Demo {
    public static void main(String[] args) throws IOException {
        System.out.println("01234567".substring(0, 0));
        Fenci fenci = new Fenci(new File("../../fxsjy/jieba/jieba/dict.txt").toURI().toURL());
        List<String> setList = fenci.cut("我来到北京清华大学", true);
        System.out.println(String.join("/ ", setList));
    }
}
