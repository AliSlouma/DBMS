package eg.edu.alexu.csd.oop.db.cs30;

import eg.edu.alexu.csd.oop.db.cs30.queries.ExtractData;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Pattern;

public class Row {
    private HashMap<String,Object> map = new HashMap<>();
    private Table myTable;
    /**
     *
     * @param columnNames columns names you want to specify
     * @param values the values of (columnNames1)
     */
    public Row(String[] columnNames, Object[] values,Table mytable) {
        myTable=mytable;

        for(int i=0;i<columnNames.length;i++){
            map.put(columnNames[i],values[i]);
        }

    }
    public Object[] getRow(String[] columnNames){
        Object[] result=new Object[columnNames.length];
        for(int i=0;i<columnNames.length;i++){
            result[i]=map.get(columnNames[i]);
        }
        return result;
    }
    public void updateRow(String[] columnNames, Object[] values){
        for(int i=0;i<columnNames.length;i++){
            map.put(columnNames[i],values[i]);
        }
    }
    public Boolean Condition(String ColumnName,char Operator,Object value) throws SQLException{
        checkCondition(ColumnName,value);
        Object val=map.get(ColumnName);
        Integer type=myTable.getMap().get(ColumnName);
        if(val == null)
            return false;
        if(type==1){
            if(Operator=='='){
                return val == value;
            }else if(Operator=='>'){
                return ((Integer) val) > ((Integer) value);
            }else if(Operator=='<'){
                return ((Integer) val) < ((Integer) value);
            }else{
                System.out.println("Not Supported Operator !");
                return false;
            }
        }else if(type==0){
            if(Operator=='='){
                return ((String) val).equals((String) value);
            }else if(Operator=='>'){
                return ((String) val).compareTo(((String) value)) > 0;
            }else if(Operator=='<'){
                return ((String) val).compareTo(((String) value)) < 0;
            }else{
                System.out.println("Not Supported Operator !");
                return false;
            }
        }else{
            System.out.println("Invalid type not 0 or 1");
            return false;
        }
    }
    private void checkCondition(String ColumnName,Object value) {
        myTable.checkColumns(new String[]{ColumnName});
        myTable.checkTypes(new String[]{ColumnName}, new Object[]{value} );
    }
    public Boolean multiCondtion(String condtion) throws SQLException {
        String[] array=split(condtion);
        Stack<Object> stack=new Stack<>();
        Integer start=0;
        stack.push(start);
        for(int i=0;i<array.length;i++){
            int id=getId(array[i]);
            int prevId=getId(stack.peek());
            if(id==1){

                if(!(prevId==5 || prevId==2)){
                    stack.push("(");
                }else
                    throw new SQLException("can't have open bracket after condtion");

            }else if(id==2){

                try {
                    Boolean poped1= (Boolean) stack.pop();
                    String poped2= (String) stack.pop();
                    if(!poped2.equals("("))
                        throw new SQLException("close bracket don't match open bracket");
                    stack.push(poped1);
                    evaluate(stack);
                }catch (ClassCastException e){
                    throw new SQLException("close bracket don't match open bracket");
                }

            }else if(id==3){
                if(prevId==5){
                    stack.push(array[i]);
                }else
                    throw new SQLException(array[i]+ " must have condion before it");
            }else if(id == 4){
                if(!(prevId==5 || prevId==2)){
                    stack.push(array[i]);
                }else
                    throw new SQLException("can't have not after condtion");
            }else if(id==5){
                Boolean result=oneCondition(array[i]);
                if( !(prevId==5 || prevId==2) ){
                    stack.push(result);
                    evaluate(stack);
                }else
                    throw new SQLException("can't have two or more conditions in row");
            }
        }
        if(stack.size()!=2 || !(stack.peek() instanceof Boolean)){
            throw new SQLException("brackets don't matching");
        }
        return (Boolean) stack.peek();
    }
    private void evaluate(Stack<Object> stack) throws SQLException {
        Boolean top= (Boolean) stack.pop();
        int prevId=getId(stack.peek());
        while (prevId==3 || prevId==4){
            if(prevId==4){
                top=!top;
                stack.pop();
            }else{
                String operator= (String) stack.pop();
                Boolean otherCondition=(Boolean) stack.pop();
                if(operator.equalsIgnoreCase("and")){
                    top=top && otherCondition;
                }else if(operator.equalsIgnoreCase("or")){
                    top=top || otherCondition;
                }else
                    throw new SQLException("not and or OR !!!");
            }
            prevId=getId(stack.peek());
        }
        stack.push(top);
    }
    private int getId(Object element) throws SQLException {
        if(element instanceof Integer)
            return 0;
        else if(element instanceof String){
            String s= (String) element;
            if( s.equals( "(" ) ){
                return 1;
            }else if(s.equals( ")" )){
                return 2;
            }else if(s.equalsIgnoreCase("and") || s.equalsIgnoreCase("or")){
                return 3;
            }else if(s.equalsIgnoreCase("not")){
                return 4;
            }else
                return 5;
        }else if(element instanceof Boolean)
            return 5;
        throw new SQLException("not String or Boolean");
    }

    private String[] split(String condition){
        condition = condition.replaceAll("(\\s*;\\s*$)", "");
        condition = condition.replaceAll("(\\s*<\\s*)", "<");
        condition = condition.replaceAll("(\\s*>\\s*)", ">");
        condition = condition.replaceAll("(\\s*=\\s*)", "=");
        condition = condition.replaceAll("(\\s*<=\\s*)", "<=");
        condition = condition.replaceAll("(\\s*>=\\s*)", ">=");

        condition = condition.replaceAll("(\\s*\\(\\s*)", " ( ");
        condition = condition.replaceAll("(\\s*\\)\\s*)", " ) ");

        return ExtractData.removeEmptyStrings(condition.split(" "));
    }

    private Boolean oneCondition(String condition) throws SQLException {
        String s=condition;

        Pattern pattern = Pattern.compile("(\\s*[^\\s]+\\s*[<>=]\\s*[^\\s]+\\s*)", Pattern.CASE_INSENSITIVE);
        if (!pattern.matcher(condition).matches())
        {
            throw new SQLException("Condition doesn't match");
        }

        Map<String , Object> table = new HashMap<>();
        String[] splitQuery = condition.split("\\s*(=|<|>)\\s*");

        if(s.toLowerCase().contains("=")) table.put("operator" , "=");
        else if(s.toLowerCase().contains(">")) table.put("operator" , ">");
        else if(s.toLowerCase().contains("<")) table.put("operator" , "<");

        table.put("condColumn" ,  splitQuery[0]);
        if (splitQuery[1].contains("'")){ table.put("condValue",splitQuery[1].replaceAll("'","")); }
        else {
            if(splitQuery[1].matches("\\d+"))
                table.put("condValue",Integer.parseInt(splitQuery[1]));
            else
                throw new SQLException("double Quote missing");
        }

        return Condition((String) table.get("condColumn"),((String)table.get("operator")).charAt(0), table.get("condValue"));
    }
}
