package com.github.argszero.fenci;

import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 */
public class Demo {


    public static void main(String[] args) throws IOException, ScriptException {
        Fenci fenci = new Fenci();
        fenci.setDict(new File("../../fxsjy/jieba/jieba/dict.txt").toURI().toURL());
        fenci.setEmitProbability(new File("/tmp/prob_emit.py").toURI().toURL());
        fenci.setTransProbability(new File("../../fxsjy/jieba/jieba/finalseg/prob_trans.py").toURI().toURL());
        fenci.setStartProbability(new File("../../fxsjy/jieba/jieba/finalseg/prob_start.py").toURI().toURL());
        List<String> setList;
        setList = fenci.cut("我来到北京清华大学", true);
        System.out.println("Full Mode: " + String.join("/ ", setList));
        setList = fenci.cut("我来到北京清华大学", false);
        System.out.println("Default Mode: " + String.join("/ ", setList));
        setList = fenci.cut("他来到了网易杭研大厦");
        System.out.println("新词识别: " + String.join("/ ", setList));
    }
}
