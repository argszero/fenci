package com.github.argszero.fenci;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.URLReader;

import javax.script.*;
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
    public static final Pattern FINAL_SKIP = Pattern.compile("(\\d+\\.\\d+|[a-zA-Z0-9]+)");
    private static Pattern HAN_CUT_ALL = Pattern.compile("([\\u4E00-\\u9FA5]+)");//汉字
    private static Pattern SKIP_CUT_ALL = Pattern.compile("[^a-zA-Z0-9+#\\n]");//英文字母，数字，回车
    private static Pattern HAN_DEFAULT = Pattern.compile("([\\u4E00-\\u9FA5a-zA-Z0-9+#&\\._]+)");//汉字，字母，数字及符号
    private static Pattern SKIP_DEFAULT = Pattern.compile("(\\r\\n|\\s)");//回车,空格
    private Dict dict;
    private Probability probability = new Probability();

    public Fenci() {
        this.dict = new Dict();
    }

    public void setDict(URL url) {
        this.dict.setDictUrl(url);
    }

    public void setStartProbability(URL url) {
        this.probability.setStartProbability(url);
    }

    public void setTransProbability(URL url) {
        this.probability.setTransProbability(url);
    }

    public void setEmitProbability(URL url) {
        this.probability.setEmitProbability(url);
    }

    public List<String> cut(String sentence) throws IOException, ScriptException {
        return cut(sentence, false, true);
    }

    public List<String> cut(String sentence, boolean cutAll/*全模式为true,精确模式为false*/) throws IOException, ScriptException {
        return cut(sentence, cutAll, true);
    }

    public List<String> cut(String sentence, boolean cutAll/*全模式为true,精确模式为false*/, boolean hmm) throws IOException, ScriptException {
        Pattern han, skip;
        if (cutAll) {
            han = HAN_CUT_ALL;
            skip = SKIP_CUT_ALL;
        } else {
            han = HAN_DEFAULT;
            skip = SKIP_DEFAULT;
        }
        List<String> wordList = new ArrayList<>();
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
        return wordList;
    }


    private void cutBlock(Pattern han, Pattern skip, String block, List<String> wordList,
                          boolean cutAll/*全模式为true,精确模式为false*/, boolean hmm) throws IOException, ScriptException {
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

    private void catHanDag(String block, List<String> wordList, boolean hmm) throws IOException, ScriptException {
        if (hmm) {
            List<List<Integer>> dag = getDag(block);
            double logTotalFreq = Math.log(dict.totalFreq);
            double[][] route = new double[block.length() + 1][];
            route[block.length()] = new double[]{0, 0};
            for (int i = block.length() - 1; i > -1; i--) {
                List<Integer> edges = dag.get(i);
                double maxWeight = -1.0;
                int maxWeightEdge = 0;
                for (Integer edge : edges) {
                    int freq = dict.getFreq(block.substring(i, edge + 1));
                    if (freq == 0) {
                        freq = 1;
                    }
                    double weight = Math.log(freq) - logTotalFreq + route[edge + 1][0];
                    if (maxWeightEdge == 0) {
                        maxWeightEdge = edge;
                        maxWeight = weight;
                    } else if (weight > maxWeight) {
                        maxWeightEdge = edge;
                        maxWeight = weight;
                    }
                }
                route[i] = new double[]{maxWeight, maxWeightEdge};
            }


            int x = 0;
            int y;
            String buf = "";
            while (x < block.length()) {
                y = (int) route[x][1] + 1;
                String word = block.substring(x, y);
                if (y - x == 1) {
                    buf += word;
                } else {
                    if (buf.length() == 1) {
                        wordList.add(buf);
                        buf = "";
                    } else {
                        Integer freq = dict.getFreq(buf);
                        if (freq < 1) {
                            finalcut(buf, wordList);
                        } else {
                            for (char c : buf.toCharArray()) {
                                wordList.add(Character.toString(c));
                            }
                        }
                        buf = "";
                    }
                    wordList.add(word);
                }
                x = y;
            }


            if (buf.length() > 0) {
                if (buf.length() == 1) {
                    wordList.add(buf);
                } else if (dict.getFreq(buf) < 1) {
                    finalcut(buf, wordList);
                } else {
                    for (char c : buf.toCharArray()) {
                        wordList.add(Character.toString(c));
                    }
                }
            }
        }
    }

    private List<String> finalcut(String sentence, List<String> wordList) throws ScriptException {
        Matcher matcher = HAN_CUT_ALL.matcher(sentence);
        int lastEnd = 0;
        while (matcher.find()) {
            p(sentence.substring(lastEnd, matcher.start()), wordList);
            p(matcher.group(), wordList);
            lastEnd = matcher.end();
        }
        if (lastEnd != sentence.length()) {
            p(sentence.substring(lastEnd), wordList);
        }
        return wordList;
    }

    private void p(String block, List<String> wordList) throws ScriptException {
        if (HAN_CUT_ALL.matcher(block).matches()) {
            __cut(block, wordList);
        } else {
            Matcher matcher = FINAL_SKIP.matcher(block);
            int lastEnd = 0;
            while (matcher.find()) {
                addIfNotEmpty(block.substring(lastEnd, matcher.start()), wordList);
                addIfNotEmpty(matcher.group(), wordList);
                lastEnd = matcher.end();
            }
            if (lastEnd != block.length()) {
                addIfNotEmpty(block.substring(lastEnd), wordList);
            }
        }
    }

    private void __cut(String sentence, List<String> wordList) throws ScriptException {
        Viterbi viterbi = viterbi(sentence, "BMES", probability.getStart(), probability.getTrans(), probability.getEmit());
        int begin = 0;
        int nexti = 0;
        for (int i = 0; i < sentence.length(); i++) {
            String pos = viterbi.posList[i];
            if (pos.equals("B")) {
                begin = i;
            } else if (pos.equals("E")) {
                wordList.add(sentence.substring(begin, i + 1));
                nexti = i + 1;
            } else if (pos.equals("S")) {
                wordList.add(sentence.substring(i, i + 1));
                nexti = i + 1;
            }
        }
        if (nexti < sentence.length()) {
            wordList.add(sentence.substring(nexti));
        }
    }

    private static Map<String, String> PrevStatus = new HashMap<>();

    {
        PrevStatus.put("B", "ES");
        PrevStatus.put("M", "MB");
        PrevStatus.put("S", "SE");
        PrevStatus.put("E", "BM");
    }

    private Viterbi viterbi(String sentence, String bmes, ScriptObjectMirror start, ScriptObjectMirror trans, ScriptObjectMirror emit) {
        List<Map<String, Double>> V = new ArrayList<>();
        V.add(new HashMap());
        Map<String, String[]> path = new HashMap<>();
        for (char c : bmes.toCharArray()) {
            String state = Character.toString(c);
            V.get(0).put(state, getDouble(start, state) + getDouble(emit, state, Character.toString(sentence.charAt(0)), -3.14e100));
            path.put(state, new String[]{state});
        }
        for (int i = 1; i < sentence.length(); i++) {
            V.add(new HashMap<>());
            Map newPath = new HashMap<>();
            for (char c : bmes.toCharArray()) {
                String state = Character.toString(c);
                double emitProbability = getDouble(emit, state, Character.toString(sentence.charAt(i)), -3.14e100);
                String maxProbState0 = null;
                double maxProb = 0;
                for (char c1 : PrevStatus.get(state).toCharArray()) {
                    String state0 = Character.toString(c1);
                    double prob = V.get(i - 1).get(state0) + getDouble(trans, state0, state, -3.14e100) + emitProbability;
                    if (maxProbState0 == null || prob > maxProb) {
                        maxProbState0 = state0;
                        maxProb = prob;
                    }
                }
                V.get(i).put(state, maxProb);
                newPath.put(state, add(path.get(maxProbState0), state));
            }
            path = newPath;
        }
        String maxProbState = null;
        double maxProb = 0;
        for (char c : "ES".toCharArray()) {
            String state = Character.toString(c);
            double prob = V.get(sentence.length() - 1).get(state);
            if (maxProbState == null || prob > maxProb) {
                maxProb = prob;
                maxProbState = state;
            }
        }
        return new Viterbi(maxProb, path.get(maxProbState));
    }

    private class Viterbi {
        private double maxProb;
        private String[] posList;

        public Viterbi(double maxProb, String[] posList) {
            this.maxProb = maxProb;
            this.posList = posList;
        }
    }

    private String[] add(String[] array, String state) {
        String[] dest = new String[array.length + 1];
        System.arraycopy(array, 0, dest, 0, array.length);
        dest[array.length] = state;
        return dest;
    }

    private double getDouble(ScriptObjectMirror obj, String key) {
        return (double) obj.get(key);
    }

    private double getDouble(ScriptObjectMirror obj, String key1, String key2, double defValue) {
        ScriptObjectMirror subObj = (ScriptObjectMirror) obj.get(key1);
        Object v = subObj.get(key2);
        if (v == null) {
            return defValue;
        }
        return (double) v;
    }

    private void addIfNotEmpty(String word, List<String> wordList) {
        if (word.length() > 0) {
            wordList.add(word);
        }
    }

    private void cutHanAll(String block, List<String> wordList) throws IOException {
        List<List<Integer>> dag = getDag(block);
        int old = -1;
        for (int i = 0; i < dag.size(); i++) {
            List<Integer> list = dag.get(i);
            if (list.size() == 1 && i > old) { //如果只有一个字，且前面的词里不包含这个字，则将这个字作为一个词
                wordList.add(block.substring(i, list.get(0) + 1));
            } else {
                for (Integer j : list) { //如果有字，且有词，则丢掉字，返回所有词
                    if (j > i) {//丢掉字
                        wordList.add(block.substring(i, j + 1));
                        old = j;
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

    /**
     * 获取Dag,即对于每个字符，如果和后面的字符可以组成一个词，则连线。比如
     * 我  来  到  北  京  清  华  大  学
     * 返回的Dag为：[0], [1,2],   [2], [3,4],   [4], [5,6,8],         [6,7],   [7,8],   [8]
     * 即:         [我],[来,来到],[到],[北,北京],[京],[清,清华,清华大学],[华,华大],[大,大学],[学]
     *
     * @param block
     * @return
     * @throws IOException
     */
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

        public void setDictUrl(URL dictUrl) {
            this.dictUrl = dictUrl;
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

    private class Probability {
        private ScriptObjectMirror start;
        private ScriptObjectMirror trans;
        private ScriptObjectMirror emit;
        private URL startProbability;
        private URL transProbability;
        private URL emitProbability;

        public ScriptObjectMirror getStart() throws ScriptException {
            if (start == null) {
                synchronized (this) {
                    if (start == null) {
                        start = read(startProbability, "P");
                    }
                }
            }
            return start;
        }

        private ScriptObjectMirror read(URL script, String var) throws ScriptException {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
            Bindings bind = engine.createBindings();
            engine.setBindings(bind, ScriptContext.ENGINE_SCOPE);
            engine.eval(new URLReader(script));
            return (ScriptObjectMirror) bind.get(var);
        }

        public ScriptObjectMirror getTrans() throws ScriptException {
            if (trans == null) {
                synchronized (this) {
                    if (trans == null) {
                        trans = read(transProbability, "P");
                    }
                }
            }
            return trans;
        }

        public ScriptObjectMirror getEmit() throws ScriptException {
            if (emit == null) {
                synchronized (this) {
                    if (emit == null) {
                        emit = read(emitProbability, "P");
                    }
                }
            }
            return emit;
        }

        public void setStartProbability(URL startProbability) {
            this.startProbability = startProbability;
        }

        public URL getStartProbability() {
            return startProbability;
        }

        public void setTransProbability(URL transProbability) {
            this.transProbability = transProbability;
        }

        public URL getTransProbability() {
            return transProbability;
        }

        public void setEmitProbability(URL emitProbability) {
            this.emitProbability = emitProbability;
        }

        public URL getEmitProbability() {
            return emitProbability;
        }
    }

}
