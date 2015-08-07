package com.github.argszero.fenci;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class Fenci {
    private static Pattern HAN_CUT_ALL = Pattern.compile("([\\u4E00-\\u9FA5]+)");//汉字
    private static Pattern SKIP_CUT_ALL = Pattern.compile("[^a-zA-Z0-9+#\\n]");//英文字母，数字，回车
    private static Pattern HAN_DEFAULT = Pattern.compile("([\\u4E00-\\u9FA5a-zA-Z0-9+#&\\._]+)");//汉字，字母，数字及符号
    private static Pattern SKIP_DEFAULT = Pattern.compile("(\\r\\n|\\s)");//回车,空格
    private Dict dict;

    public Fenci(URL dic) {
        this.dict = new Dict(dic);
    }

    public Fenci() {
        this.dict = new Dict();
    }

    public List<String> cut(String sentence, boolean cutAll/*全模式为true,精确模式为false*/) throws IOException {
        return cut(sentence, cutAll, true);
    }

    public List<String> cut(String sentence, boolean cutAll/*全模式为true,精确模式为false*/, boolean hmm) throws IOException {
        Pattern han, skip;
        if (cutAll) {
            han = HAN_CUT_ALL;
            skip = SKIP_CUT_ALL;
        } else {
            han = HAN_DEFAULT;
            skip = SKIP_DEFAULT;
        }
        List<String> wordList = new ArrayList<>();
        if (cutAll) {
            Matcher matcher = han.matcher(sentence);
            int lastEnd = 0;
            while (matcher.find()) {
                cutBlock(han, skip, sentence.substring(lastEnd, matcher.start()), wordList, cutAll, hmm);
                cutBlock(han, skip, matcher.group(), wordList, cutAll, hmm);

                lastEnd = matcher.end();
            }
            if (lastEnd != sentence.length()) {
                cutBlock(han, skip, sentence.substring(lastEnd), wordList, cutAll, hmm);
            }
        }
        return wordList;
    }


    private void cutBlock(Pattern han, Pattern skip, String block, List<String> wordList,
                          boolean cutAll/*全模式为true,精确模式为false*/, boolean hmm) throws IOException {
        if (han.matcher(block).matches()) {
            if (cutAll) {
                cutHanAll(block, wordList);
            } else {
                catHanDag(block, wordList, hmm);
            }
        } else {
            Matcher matcher = skip.matcher(block);
            int lastEnd = 0;
            while (matcher.find()) {
                cutSkipBlock(skip, block.substring(lastEnd, matcher.start()), wordList, cutAll);
                cutSkipBlock(skip, matcher.group(), wordList, cutAll);
                lastEnd = matcher.end();
            }
            if (lastEnd != block.length()) {
                cutSkipBlock(skip, block.substring(lastEnd), wordList, cutAll);
            }
        }
    }

    private void catHanDag(String block, List<String> wordList, boolean hmm) {
        //TODO
    }

    private void cutHanAll(String block, List<String> wordList) throws IOException {
        List<List<Integer>> dag = getDag(block);
        int old=-1;
        for (int i = 0; i < dag.size(); i++) {
            List<Integer> list = dag.get(i);
            if(list.size()==1 && i>old){
                wordList.add(block.substring(i,list.get(0)+1));
            }else{
                for(Integer j :list){
                    if(j>i){
                        wordList.add(block.substring(i,j+1));
                        old=j;
                    }
                }
            }

        }
    }

    private void cutSkipBlock(Pattern skip, String skipBlock, List<String> wordList, boolean cutAll) {
        if (!skip.matcher(skipBlock).matches() && !cutAll) {
            wordList.add(skipBlock);
        } else {
            for (char c : skipBlock.toCharArray()) {
                wordList.add(Character.toString(c));
            }
        }
    }

    private List<List<Integer>> getDag(String block) throws IOException {
        char[] chars = block.toCharArray();
        List<List<Integer>> dag = new ArrayList<>();
        for (int i = 0; i < chars.length; i++) {
            List<Integer> tmpList = new ArrayList<>();
            for (int j = i; j < chars.length; j++) {
                if (j == i) {
                    tmpList.add(i);
                } else {
                    int freq = dict.getFreq(block.substring(i, j + 1));
                    if (freq == -1) {
                        break;
                    } else if (freq > 0) {
                        tmpList.add(j);
                    }
                }
            }
            dag.add(tmpList);
        }
        return dag;
    }


    private class Dict {
        private URL dictUrl;
        private int totalFreq;
        private Map<String, Integer> freqMap;

        public Dict(URL dic) {
            this.dictUrl = dic;

        }

        public Dict() {
            try {
                dictUrl = new URL("https://github.com/fxsjy/jieba/raw/master/jieba/dict.txt");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        public Map<String, Integer> getFreqMap() throws IOException {
            if (freqMap == null) {
                synchronized (this) {
                    if (freqMap == null) {
                        freqMap = new HashMap<>();
                        System.out.println("Building prefix dict from:" + dictUrl);
                        BufferedReader reader = null;
                        try {
                            reader = new BufferedReader(new InputStreamReader(dictUrl.openStream()));
                            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                                String[] split = line.split(" ");
                                try {
                                    String word = split[0];
                                    int freq = Integer.parseInt(split[1]);
                                    totalFreq += freq;
                                    freqMap.put(word, freq);
                                    for (int i = 0; i < word.length(); i++) {
                                        freqMap.putIfAbsent(word.substring(0, i + 1), 0);
                                    }

                                } catch (Exception e) {
                                    throw new RuntimeException("invalid dictionary entry in " + dictUrl + " at line:" + line);
                                }
                            }
                        } finally {
                            if (reader != null) {
                                reader.close();
                            }
                        }
                    }
                }
            }
            return freqMap;
        }

        public int getFreq(String word) throws IOException {
            Integer freq = getFreqMap().get(word);
            if (freq == null) {
                return -1;
            }
            return freq;
        }
    }

}
