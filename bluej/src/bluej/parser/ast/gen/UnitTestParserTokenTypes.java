// $ANTLR 2.7.2: "unittest.tree.g" -> "UnitTestParser.java"$

    package bluej.parser.ast.gen;
    
    import bluej.parser.SourceSpan;
    import bluej.parser.SourceLocation;
	import bluej.parser.ast.LocatableAST;
	    
    import java.util.*;
    import antlr.BaseAST;

public interface UnitTestParserTokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int BLOCK = 4;
	int MODIFIERS = 5;
	int OBJBLOCK = 6;
	int SLIST = 7;
	int CTOR_DEF = 8;
	int METHOD_DEF = 9;
	int VARIABLE_DEF = 10;
	int INSTANCE_INIT = 11;
	int STATIC_INIT = 12;
	int TYPE = 13;
	int CLASS_DEF = 14;
	int INTERFACE_DEF = 15;
	int PACKAGE_DEF = 16;
	int ARRAY_DECLARATOR = 17;
	int EXTENDS_CLAUSE = 18;
	int IMPLEMENTS_CLAUSE = 19;
	int PARAMETERS = 20;
	int PARAMETER_DEF = 21;
	int LABELED_STAT = 22;
	int TYPECAST = 23;
	int INDEX_OP = 24;
	int POST_INC = 25;
	int POST_DEC = 26;
	int METHOD_CALL = 27;
	int EXPR = 28;
	int ARRAY_INIT = 29;
	int IMPORT = 30;
	int UNARY_MINUS = 31;
	int UNARY_PLUS = 32;
	int CASE_GROUP = 33;
	int ELIST = 34;
	int FOR_INIT = 35;
	int FOR_CONDITION = 36;
	int FOR_ITERATOR = 37;
	int EMPTY_STAT = 38;
	int FINAL = 39;
	int ABSTRACT = 40;
	int STRICTFP = 41;
	int SUPER_CTOR_CALL = 42;
	int CTOR_CALL = 43;
	int COMMENT_DEF = 44;
	int LITERAL_package = 45;
	int SEMI = 46;
	int LITERAL_import = 47;
	int LBRACK = 48;
	int RBRACK = 49;
	int LITERAL_void = 50;
	int LITERAL_boolean = 51;
	int LITERAL_byte = 52;
	int LITERAL_char = 53;
	int LITERAL_short = 54;
	int LITERAL_int = 55;
	int LITERAL_float = 56;
	int LITERAL_long = 57;
	int LITERAL_double = 58;
	int IDENT = 59;
	int DOT = 60;
	int STAR = 61;
	int LITERAL_private = 62;
	int LITERAL_public = 63;
	int LITERAL_protected = 64;
	int LITERAL_static = 65;
	int LITERAL_transient = 66;
	int LITERAL_native = 67;
	int LITERAL_threadsafe = 68;
	int LITERAL_synchronized = 69;
	int LITERAL_volatile = 70;
	int LITERAL_class = 71;
	int LITERAL_extends = 72;
	int LITERAL_interface = 73;
	int LCURLY = 74;
	int RCURLY = 75;
	int COMMA = 76;
	int LITERAL_implements = 77;
	int LPAREN = 78;
	int RPAREN = 79;
	int LITERAL_this = 80;
	int LITERAL_super = 81;
	int ASSIGN = 82;
	int LITERAL_throws = 83;
	int COLON = 84;
	int LITERAL_if = 85;
	int LITERAL_else = 86;
	int LITERAL_for = 87;
	int LITERAL_while = 88;
	int LITERAL_do = 89;
	int LITERAL_break = 90;
	int LITERAL_continue = 91;
	int LITERAL_return = 92;
	int LITERAL_switch = 93;
	int LITERAL_throw = 94;
	int LITERAL_assert = 95;
	int LITERAL_case = 96;
	int LITERAL_default = 97;
	int LITERAL_try = 98;
	int LITERAL_finally = 99;
	int LITERAL_catch = 100;
	int PLUS_ASSIGN = 101;
	int MINUS_ASSIGN = 102;
	int STAR_ASSIGN = 103;
	int DIV_ASSIGN = 104;
	int MOD_ASSIGN = 105;
	int SR_ASSIGN = 106;
	int BSR_ASSIGN = 107;
	int SL_ASSIGN = 108;
	int BAND_ASSIGN = 109;
	int BXOR_ASSIGN = 110;
	int BOR_ASSIGN = 111;
	int QUESTION = 112;
	int LOR = 113;
	int LAND = 114;
	int BOR = 115;
	int BXOR = 116;
	int BAND = 117;
	int NOT_EQUAL = 118;
	int EQUAL = 119;
	int LT = 120;
	int GT = 121;
	int LE = 122;
	int GE = 123;
	int LITERAL_instanceof = 124;
	int SL = 125;
	int SR = 126;
	int BSR = 127;
	int PLUS = 128;
	int MINUS = 129;
	int DIV = 130;
	int MOD = 131;
	int INC = 132;
	int DEC = 133;
	int BNOT = 134;
	int LNOT = 135;
	int LITERAL_true = 136;
	int LITERAL_false = 137;
	int LITERAL_null = 138;
	int LITERAL_new = 139;
	int NUM_INT = 140;
	int CHAR_LITERAL = 141;
	int STRING_LITERAL = 142;
	int NUM_FLOAT = 143;
	int NUM_LONG = 144;
	int NUM_DOUBLE = 145;
	int WS = 146;
	int SL_COMMENT = 147;
	int ML_COMMENT = 148;
	int ESC = 149;
	int HEX_DIGIT = 150;
	int IDENT_LETTER = 151;
	int EXPONENT = 152;
	int FLOAT_SUFFIX = 153;
	int LITERAL_const = 154;
}
