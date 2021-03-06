package codegen.ast;

import codegen.SemanticError;
import codegen.ast.literal.Literal;

import java.util.stream.Stream;

public class ExpressionNode extends SimpleNode {
    private boolean isIdentifier;
    private String resultName;
    private Type type;

    public ExpressionNode() {
        super(NodeType.EXPRESSION_STATEMENT);
    }

    public boolean isIdentifier() {
        return isIdentifier;
    }

    public void setIsIdentifier() throws Exception {
        if (this.getChild(0).getNodeType() == NodeType.VAR_USE) {
            //EXPR -> VAR_USE -> ID
            IdentifierNode id = ((IdentifierNode) this.getChild(0).getChild(0));
            if (!id.getValue().startsWith("%")) {
                resultName = "%" + id.getValue();
            } else {
                resultName = id.getValue();
            }
            if (id.getDSCP() == null)
                throw new SemanticError(id.getValue() + " not declared");
            type = id.getDSCP().getType();
        } else if (Stream.of(NodeType.ADDITION, NodeType.SUBTRACTION, NodeType.MULTIPLICATION, NodeType.DIVISION, NodeType.MOD).anyMatch(nodeType -> this.getChild(0).getNodeType().equals(nodeType))) {
            type = this.getChild(0).getDSCP().getType();
            resultName = this.getChild(0).getDSCP().getValue();
            this.setDSCP(this.getChild(0).getDSCP());
        } else if (this.getChild(0).getNodeType().equals(NodeType.IDENTIFIER)) {
            type = this.getChild(0).getDSCP().getType();
            resultName = this.getChild(0).getDSCP().getValue();
            this.setDSCP(this.getChild(0).getDSCP());
        } else if (this.getChild(0).getNodeType().equals(NodeType.READ_LINE)) {
            type = this.getChild(0).getDSCP().getType();
            resultName = "\"ReadLine()\"";
        } else {
            //EXPR -> LITERAL
            if (!this.getChild(0).getDSCP().getValue().equals("readInteger()")) {
                Literal literal = (Literal) this.getChild(0);
                resultName = this.getChild(0).toString();
                type = literal.getType();
            } else {
                type = this.getChild(0).getDSCP().getType();
            }

        }
        isIdentifier = true;
    }

    public String getResultName() {
        return resultName;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}
