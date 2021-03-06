grammar OnlyWenExpression;

/* Lexical rules */

BRANCH: 'branch' ;

IS : 'is' ;
IN : 'in' ;

OPAR : '(' ;
CPAR : ')' ;
COMMA : ',' ;

// DECIMAL, IDENTIFIER, COMMENTS, WS are set using regular expressions
QUOTED_STRING: '\'' ('\\'. | '\'\'' | ~('\'' | '\\'))* '\'';
STRING: [a-zA-Z0-9_\-#/]+;
WS : [ \r\t\u000C\n]+ -> skip ;

/* Grammar rules */

/*
criterion
    : comparisonExpression EOF
    ;
*/
expression
    : variable operator operand
    ;

variable
    : BRANCH
    ;

operand
    : STRING
    | QUOTED_STRING
    | OPAR operand_list CPAR
    ;

operand_list
    : operand COMMA operand_list
    | operand
    ;


operator
    : IS
    | IN
    ;
