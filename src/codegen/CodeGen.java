package codegen;

import codegen.ast.*;
import codegen.ast.literal.Literal;
import codegen.symboltable.DSCP;
import codegen.symboltable.SymbolTable;
import codegen.vtable.CodeForFunct;
import codegen.vtable.VTable;

import java.io.FileWriter;
import java.io.IOException;

public class CodeGen {

    private static final SymbolTable spaghettiStack = new SymbolTable();
    private static final VTable vTable = new VTable();

    public static String dataSeg = ".data\n";
    public static String textSeg = ".text\n";

    static {
        dataSeg += "\tzeroDouble: \t.double \t0.0\n";
        textSeg += "\tldc1\t$f0, zeroDouble\n";
    }

    public static void cgen(Node node) throws Exception {
        switch (node.getNodeType()) {
            case METHOD_DECLARATION:
                cgenMethodDeclaration(node);
                break;
            case BLOCK:
                cgenBlock(node);
                break;
            case VARIABLE_DECLARATION:
                cgenVariableDecl(node);
                break;
            case IDENTIFIER:
                cgenIdentifier(node);
                break;
            case ASSIGN:
                cgenAssign(node);
                break;
            case EXPRESSION_STATEMENT:
                cgenExpressionStatement(node);
                break;
            case LITERAL:
                cgenLiteral((Literal) node);
                break;
            case ADDITION:
                cgenAdditon(node);
                break;
            case SUBTRACTION:
                cgenSubtraction(node);
                break;
            case MULTIPLICATION:
                cgenMultiplication(node);
                break;
            case DIVISION:
                cgenDivision(node);
                break;
            case PRINT_STATEMENT:
                cgenPrint(node);
                break;
            case ARGUMENT:
                cgenArgument(node);
                break;
            case FUNCTION_CALL:
                cgenFunctionCall(node);
                break;
            case PARAMETERS:
                cgenParameters(node);
                break;
            default:
                cgenAllChildren(node);
                break;
        }
    }

    private static void cgenMethodDeclaration(Node node) throws Exception {
        //type
        PrimitiveNode returnNode = (PrimitiveNode) node.getChild(0);
        Type methodType = returnNode.getType();
        //identifier
        IdentifierNode identifierNode = (IdentifierNode) node.getChild(1);
        String methodName = identifierNode.getValue();  //TODO add to vTable
        textSeg += methodName + ":\n";
        //arguments
        spaghettiStack.enterScope(String.valueOf(node.getChild(1)));
        cgen(node.getChild(2));
        //body
        cgen(node.getChild(3));
        if (methodName.equals("main")){
            textSeg += "\t# This line is going to signal end of program.\n";
            textSeg += "\tli\t$v0, 10\n";
            textSeg += "\tsyscall\n";
        } else {
            textSeg += "\tjr\t$ra\n";
        }

        CodeForFunct code = new CodeForFunct(methodName, "", methodType);
        vTable.addFunction(methodName, code);
    }

    private static void cgenLiteral(Literal node) throws Exception {
        DSCP dscp = new DSCP(node.getType(), null);
        dscp.setValue(String.valueOf(node));
        node.setDSCP(dscp);
        ((ExpressionNode) node.getParent()).setIsIdentifier();
    }

    private static void cgenIdentifier(Node node) throws Exception {
        IdentifierNode identifierNode = (IdentifierNode) node;
        String entry = identifierNode.getValue();
        if (node.getParent().getNodeType().equals(NodeType.FUNCTION_CALL)){ //TODO for double identifiers
            cgenAllChildren(node);
            textSeg += "\tjal\t" + node + '\n';
        } else if (node.getParent().getNodeType().equals(NodeType.EXPRESSION_STATEMENT)) {
            DSCP dscp = spaghettiStack.getDSCP(entry);
            node.setDSCP(dscp);
            ((ExpressionNode)node.getParent()).setIsIdentifier();
            // load scopeName_Entry into $v1 and send it up
            if (spaghettiStack.getDSCP(entry).isArgument()){
                int argumentPlace = spaghettiStack.getDSCP(entry).getArgumentPlace()-1;
                textSeg += "\tmove\t$v1, $a" + argumentPlace + "\n";
            } else {
                textSeg += "\tlw\t$v1, " + spaghettiStack.getEntryScope(entry)+"."+entry + "\n";
            }
        }
    }

    private static void cgenExpressionStatement(Node node) throws Exception {
        cgen(node.getChild(0));
        node.setDSCP(node.getChild(0).getDSCP());

        //don't change $v1
    }

    private static void cgenAssign(Node node) throws Exception {
        IdentifierNode identifierNode = (IdentifierNode) node.getChild(0);
        ExpressionNode expressionNode = (ExpressionNode) node.getChild(1);

        cgen(expressionNode);

        String value = null;

        if (expressionNode.getChild(0).getNodeType().equals(NodeType.FUNCTION_CALL)){
//            textSeg += ""
        } else {
            value = expressionNode.getDSCP().getValue();
        }

        String entry = identifierNode.toString();
        DSCP identifierDSCP = spaghettiStack.getDSCP(entry);

        if (!identifierDSCP.getType().equals(expressionNode.getDSCP().getType()))
            throw new Error("Type of assign doesn't match " + identifierDSCP.getType() + " -> " + expressionNode.getDSCP().getType());

        if (value != null) {    // Simple Assign
            identifierDSCP.setValue(value);
            switch (node.getChild(1).getDSCP().getType().getPrimitive()){
                case INT:
                    textSeg += "\t\t\t\t\t\t\t#Begin assign int" + spaghettiStack.getEntryScope(entry).toString()+"."+entry + '\n';
                    textSeg += "\tla\t$a0, " + spaghettiStack.getEntryScope(entry).toString()+"."+entry + '\n';
                    textSeg += "\tli\t$a1, " + value + '\n';
                    textSeg += "\tsw\t$a1  0($a0)" + "\t\t\t\t\t\t# End assign\n";
                    break;
                case DOUBLE:
                    textSeg += "\t\t\t\t\t\t\t#Begin assign double" + spaghettiStack.getEntryScope(entry).toString()+"."+entry + '\n';
                    textSeg += "\tla\t$a0, " + spaghettiStack.getEntryScope(entry).toString()+"."+entry + '\n';
                    textSeg += "\tli.d\t$f10, " + value + '\n'; //TODO check that this line is correct or not
                    textSeg += "\tsdc1\t$f10  0($a0)" + "\t\t\t\t\t\t# End assign\n";
                    break;
            }
        } else {    // with function assign
            textSeg += "\tla\t$a0, " + spaghettiStack.getEntryScope(entry).toString()+"."+entry + '\n';
            textSeg += "\tsw\t$v1  0($a0)" + "\t\t\t\t\t\t# End assign\n";
        }

        //continue code generating
        cgen(node.getChild(2));
    }

    private static void cgenVariableDecl(Node node) throws Exception {
        Type typePrimitive = ((PrimitiveNode) node.getChild(0)).getType();
        IdentifierNode identifierNode = (IdentifierNode) node.getChild(1);

        String data_id = spaghettiStack + "." + identifierNode.getValue() + ':';
        dataSeg += '\t' + data_id + '\t' + typePrimitive.getSignature() + '\t' + typePrimitive.getInitialValue() + '\n';

        if(node.getParent().getNodeType().equals(NodeType.ARGUMENT)){ // inside arguments declarations
            DSCP dscp = new DSCP(typePrimitive, identifierNode);
            dscp.setArgumentTrue(SymbolTable.getCurrentScope().getArgumentCounter());
            spaghettiStack.addEntry(identifierNode.getValue(), dscp);
        }else{ // inside body declaration
//        dscp.setConstant(); //TODO
            DSCP dscp = new DSCP(typePrimitive, identifierNode);
            spaghettiStack.addEntry(identifierNode.getValue(), dscp);
        }
    }

    private static void cgenBlock(Node node) throws Exception {
        cgenAllChildren(node);
        spaghettiStack.leaveScope();
    }

    private static void cgenAllChildren(Node node) throws Exception {
        for (Node child : node.getChildren()) {
            cgen(child);
        }
    }

    private static void cgenAdditon(Node node) throws Exception {
        ExpressionNode leftChild = (ExpressionNode) node.getChild(0);
        ExpressionNode rightChild = (ExpressionNode) node.getChild(1);
        cgen(leftChild);
        cgen(rightChild);
        Type type = widen(leftChild, rightChild);

        DSCP dscp = new DSCP(type, null);
        String value = null;
        if (type.equals(PrimitiveType.INT))
            value = String.valueOf(Integer.parseInt(leftChild.getResultName()) + Integer.parseInt(rightChild.getResultName()));
        else if (type.equals(PrimitiveType.DOUBLE))
            value = String.valueOf(Double.parseDouble(leftChild.getResultName()) + Double.parseDouble(rightChild.getResultName()));
        dscp.setValue(value);
        node.setDSCP(dscp);

        ExpressionNode parent = (ExpressionNode) node.getParent();
        parent.setIsIdentifier();
    }

    private static void cgenSubtraction(Node node) throws Exception {
        ExpressionNode leftChild = (ExpressionNode) node.getChild(0);
        ExpressionNode rightChild = (ExpressionNode) node.getChild(1);
        cgen(leftChild);
        cgen(rightChild);
        Type type = widen(leftChild, rightChild);

        DSCP dscp = new DSCP(type, null);
        String value = null;
        if (type.equals(PrimitiveType.INT))
            value = String.valueOf(Integer.parseInt(leftChild.getResultName()) - Integer.parseInt(rightChild.getResultName()));
        else if (type.equals(PrimitiveType.DOUBLE))
            value = String.valueOf(Double.parseDouble(leftChild.getResultName()) - Double.parseDouble(rightChild.getResultName()));
        dscp.setValue(value);
        node.setDSCP(dscp);

        ExpressionNode parent = (ExpressionNode) node.getParent();
        parent.setIsIdentifier();
    }

    private static void cgenMultiplication(Node node) throws Exception {
        boolean isArgument = false;

        // Calculate left child
        ExpressionNode leftChild = (ExpressionNode) node.getChild(0);
        cgen(leftChild);
        if (leftChild.getDSCP().isArgument()) {
            isArgument = true;
            // save callee registers $s0, $s1
            textSeg += "\taddi\t$sp, $sp, -8\n";
            textSeg += "\tsw\t$s0, 0($sp)\n";
            textSeg += "\tsw\t$s1, 0($sp)\n";
            if (leftChild.getDSCP().getType().equals(PrimitiveType.INT)) {
                textSeg += "\tmove\t$s0, $v1\n";
            } else if (leftChild.getDSCP().getType().equals(PrimitiveType.DOUBLE)) {
                textSeg += "\tmfc1.d\t$s0, $f10\n"; // store in $s0, $s1
            }
        }
        ExpressionNode rightChild = (ExpressionNode) node.getChild(1);
        cgen(rightChild);
        if(rightChild.getDSCP().isArgument()){
            isArgument = true;
            // save callee registers $s2, $s3
            textSeg += "\taddi\t$sp, $sp, -8\n";
            textSeg += "\tsw\t$s2, 0($sp)\n";
            textSeg += "\tsw\t$s3, 0($sp)\n";
            if(rightChild.getDSCP().getType().equals(PrimitiveType.INT)){
                textSeg += "\tmove\t$s2, $v1\n";
            }else if(rightChild.getDSCP().getType().equals(PrimitiveType.DOUBLE)){
                textSeg += "\tmfc1.d\t$s2, $f10\n"; // store in $s2, $s3
            }
        }
        Type type = widen(leftChild, rightChild);

        DSCP dscp = new DSCP(type, null);
        String value = null;
        if (type.equals(PrimitiveType.INT)) {
            int leftValue;
            int rightValue;
            if(!leftChild.getDSCP().isArgument() && !rightChild.getDSCP().isArgument()){    //Constant Folding
                leftValue = Integer.parseInt(leftChild.getResultName());
                rightValue = Integer.parseInt(rightChild.getResultName());
                value = String.valueOf(leftValue * rightValue);
            } else if (leftChild.getDSCP().isArgument() && !rightChild.getDSCP().isArgument()) {
                rightValue = Integer.parseInt(rightChild.getResultName());
                textSeg += "\tmul\t$v1, $s0, " + rightValue + '\n';
                textSeg += "\tlw\t$s1, 0($sp)\n";
                textSeg += "\tlw\t$s0, 0($sp)\n";
                textSeg += "\taddi\t$sp, $sp, 8\n";
            } else if (!leftChild.getDSCP().isArgument() && rightChild.getDSCP().isArgument()){
                leftValue = Integer.parseInt(leftChild.getResultName());
                textSeg += "\tmul\t$v1, $s0, " + leftValue + '\n';
                textSeg += "\tlw\t$s3, 0($sp)\n";
                textSeg += "\tlw\t$s2, 0($sp)\n";
                textSeg += "\taddi\t$sp, $sp, 8\n";
            } else {
                textSeg += "\tmul\t$v1, $s0, $s2\n";
                textSeg += "\tlw\t$s3, 0($sp)\n";
                textSeg += "\tlw\t$s2, 0($sp)\n";
                textSeg += "\tlw\t$s1, 0($sp)\n";
                textSeg += "\tlw\t$s0, 0($sp)\n";
                textSeg += "\taddi\t$sp, $sp, 16\n";
            }
        }else if (type.equals(PrimitiveType.DOUBLE)) {  //TODO
            value = String.valueOf(Double.parseDouble(leftChild.getResultName()) * Double.parseDouble(rightChild.getResultName()));
        }

        dscp.setValue(value);
        if(isArgument)
            dscp.setArgumentTrue(-1);
        node.setDSCP(dscp);

        ExpressionNode parent = (ExpressionNode) node.getParent();
        parent.setIsIdentifier();
    }

    private static void cgenDivision(Node node) throws Exception {
        ExpressionNode leftChild = (ExpressionNode) node.getChild(0);
        ExpressionNode rightChild = (ExpressionNode) node.getChild(1);
        cgen(leftChild);
        cgen(rightChild);
        Type type = widen(leftChild, rightChild);

        DSCP dscp = new DSCP(type, null);
        String value = null;
        if (type.equals(PrimitiveType.INT))
            value = String.valueOf(Integer.parseInt(leftChild.getResultName()) / Integer.parseInt(rightChild.getResultName()));
        else if (type.equals(PrimitiveType.DOUBLE))
            value = String.valueOf(Double.parseDouble(leftChild.getResultName()) / Double.parseDouble(rightChild.getResultName()));
        dscp.setValue(value);
        node.setDSCP(dscp);

        ExpressionNode parent = (ExpressionNode) node.getParent();
        parent.setIsIdentifier();
    }

    private static void cgenPrint(Node node) throws Exception { //TODO newline after print
        cgen(node.getChild(0));
        switch (node.getChild(0).getDSCP().getType().getPrimitive()){
            case INT:
                textSeg += "\tli\t$v0, 1\n";
                textSeg += "\tadd\t$a0, $v1, $zero\n";
                textSeg += "\tsyscall\n";
                break;
            case DOUBLE:
                textSeg += "\tli\t$v0, 3\n";
                textSeg += "\tadd.d\t$f12, $f10, $f0\n";
                textSeg += "\tsyscall\n";
                break;
            default:
                break;
        }
        System.out.println(node.getChild(0).getDSCP().getValue());
        cgen(node.getChild(1));
    }

    private static void cgenArgument(Node node) throws Exception {
        SymbolTable.getCurrentScope().addArgumentCounter();
        cgenVariableDecl(node.getChild(0));
    }

    private static void cgenFunctionCall(Node node) throws Exception {
        cgen(node.getChild(1)); // First cgen parameters
        cgen(node.getChild(0)); // Then cgen identifier

        IdentifierNode identifierNode = (IdentifierNode) node.getChild(0);
        DSCP dscp = new DSCP(vTable.getFunction(identifierNode.getValue()).getReturnType(), null);
        node.setDSCP(dscp);
    }

    private static void cgenParameters(Node node) throws Exception {
        for (int i = 0; i < node.getChild(0).getChildren().size(); i++) {
            pushRegistersA();
            cgenAllChildren(node.getChild(0).getChild(i));
            popRegistersA();
            textSeg += "\tmove\t$a"+i+", $v1\n";
        }
    }

    /**
     * Save callee saved registers s0, s1, s2, s3, s4, s5,
     */
    private static void pushRegistersS(){
        textSeg += "\taddi\t$sp, $sp, -20\n";
        textSeg += "\tsw\t$s0, 0($sp)\n";
        textSeg += "\tsw\t$s1, 4($sp)\n";
        textSeg += "\tsw\t$s2, 8($sp)\n";
        textSeg += "\tsw\t$s3, 12($sp)\n";
        textSeg += "\tsw\t$s4, 16($sp)\n";
        textSeg += "\tsw\t$s5, 20($sp)\n";
    }

    private static void popRegistersS(){
        textSeg += "\tlw\t$s0, 0($sp)\n";
        textSeg += "\tlw\t$s1, 4($sp)\n";
        textSeg += "\tlw\t$s2, 8($sp)\n";
        textSeg += "\tlw\t$s3, 12($sp)\n";
        textSeg += "\tlw\t$s4, 16($sp)\n";
        textSeg += "\tlw\t$s5, 20($sp)\n";
        textSeg += "\taddi\t$sp, $sp, 20\n";
    }

    /**
     * Save argument registers a0, a1, a2, a3
     */
    private static void pushRegistersA(){
        textSeg += "\taddi\t$sp, $sp, -16\n";
        textSeg += "\tsw\t$a0, 0($sp)\n";
        textSeg += "\tsw\t$a1, 4($sp)\n";
        textSeg += "\tsw\t$a2, 8($sp)\n";
        textSeg += "\tsw\t$a3, 12($sp)\n";
    }

    private static void popRegistersA(){
        textSeg += "\tlw\t$a0, 0($sp)\n";
        textSeg += "\tlw\t$a1, 4($sp)\n";
        textSeg += "\tlw\t$a2, 8($sp)\n";
        textSeg += "\tlw\t$a3, 12($sp)\n";
        textSeg += "\taddi\t$sp, $sp, 16\n";
    }


    private static Type widen(ExpressionNode leftChild, ExpressionNode rightChild) throws Exception {
        if(leftChild.getDSCP().getType().equals(rightChild.getDSCP().getType())) {
            Type type = leftChild.getDSCP().getType();
            if (type.equals(PrimitiveType.INT) || type.equals(PrimitiveType.DOUBLE))
                return type;
            throw new Exception("can't do operation on " + type);
        }
        throw new Exception("can't do operation on " + leftChild.getType() + " and " + rightChild.getType());
    }

    public static void compile() throws IOException {
        FileWriter out = new FileWriter("out.asm");
        out.write(dataSeg + textSeg);
        out.close();
    }

    public static String getDataSeg() {
        return dataSeg;
    }

    public static String getTextSeg() {
        return textSeg;
    }
}
