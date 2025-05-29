package diced.bread.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionalParser {
    private String ifFalse;
    private String ifTrue;
    private String condition;
    private boolean value = true;

    public ConditionalParser(String insert){
        // Example input: "(condition)?trueVal:falseVal"
        Pattern pattern = Pattern.compile("\\[\\s*(?:([^\\[\\]?/:]+)\\?)?([^\\[\\]?/:]+):([^\\[\\]?/:]+)\\s*\\]");
        Matcher matcher = pattern.matcher(insert.strip().trim());

        if (matcher.matches()) {
            condition = matcher.group(1).trim();
            ifTrue = matcher.group(2).trim();
            ifFalse = matcher.group(3).trim();        
        } else {
            throw new IllegalArgumentException("Input does not match expected format: (condition)?trueVal:falseVal");
        }
    }

    public static boolean isValid(String insert){
        Pattern pattern = Pattern.compile("\\[\\s*(?:([^\\[\\]?/:]+)\\?)?([^\\[\\]?/:]+):([^\\[\\]?/:]+)\\s*\\]");
        Matcher matcher = pattern.matcher(insert.strip().trim());
        System.out.println(matcher.matches());
        return matcher.matches();
    }

    public boolean isValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public String getConditionContext() {
        return condition;
    }

    public String getResult(){
        return (value) ? ifTrue : ifFalse;
    }
}
