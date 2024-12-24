**flow:**

![flow](diagram/flow.png)

```
flow     ::= definition*
```

**definition:**

![definition](diagram/definition.png)

```
definition
         ::= flowDefinition
           | constantDefinition
           | jsonataDefinition
```

referenced by:

* flow

**flowDefinition:**

![flowDefinition](diagram/flowDefinition.png)

```
flowDefinition
         ::= description? simpleIdentifier defaultList? '=' runnable
```

referenced by:

* definition

**constantDefinition:**

![constantDefinition](diagram/constantDefinition.png)

```
constantDefinition
         ::= description? simpleIdentifier ':=' nonEnumLiteral
```

referenced by:

* definition

**jsonataDefinition:**

![jsonataDefinition](diagram/jsonataDefinition.png)

```
jsonataDefinition
         ::= description? simpleIdentifier '(' ( parameterDefinition ','? )* ')' ':' typeSpecification jsonataCode
```

referenced by:

* definition

**description:**

![description](diagram/description.png)

```
description
         ::= '"""' ( string_not_containing - '"""' ) '"""'
```

referenced by:

* constantDefinition
* default
* flowDefinition
* jsonataDefinition
* parameterDefinition

**defaultList:**

![defaultList](diagram/defaultList.png)

```
defaultList
         ::= '(' ( default ','? )+ ')'
```

referenced by:

* flowDefinition

**default:**

![default](diagram/default.png)

```
default  ::= description? simpleIdentifier defaultTypeAndOrValue
```

referenced by:

* defaultList

**defaultTypeAndOrValue:**

![defaultTypeAndOrValue](diagram/defaultTypeAndOrValue.png)

```
defaultTypeAndOrValue
         ::= ':' typeSpecification ( '=' literal )?
           | '=' literal
```

referenced by:

* default

**typeSpecification:**

![typeSpecification](diagram/typeSpecification.png)

```
typeSpecification
         ::= ( simpleIdentifier | '[' typeSpecification ']' ) '!'?
```

referenced by:

* defaultTypeAndOrValue
* jsonataDefinition
* parameterDefinition
* typeSpecification

**runnable:**

![runnable](diagram/runnable.png)

```
runnable ::= step ( '|' step )*
```

referenced by:

* conditionalBranch
* flowDefinition
* forkBranch

**step:**

![step](diagram/step.png)

```
step     ::= singleStep
           | fork
           | conditional
```

referenced by:

* runnable

**simpleIdentifier:**

![simpleIdentifier](diagram/simpleIdentifier.png)

```
simpleIdentifier
         ::= [a-zA-Z] [a-zA-Z0-9_]*
           | '_' ( [a-zA-Z0-9] [a-zA-Z0-9_]* )?
```

referenced by:

* argument
* conditional
* constantDefinition
* default
* enumLiteral
* flowDefinition
* fork
* forkBranch
* identifier
* jsonField
* jsonataDefinition
* parameterDefinition
* scopedIdentifier
* singleStep
* typeSpecification

**literal:**

![literal](diagram/literal.png)

```
literal  ::= enumLiteral
           | nonEnumLiteral
```

referenced by:

* defaultTypeAndOrValue

**enumLiteral:**

![enumLiteral](diagram/enumLiteral.png)

```
enumLiteral
         ::= "'" simpleIdentifier "'"?
```

referenced by:

* literal

**nonEnumLiteral:**

![nonEnumLiteral](diagram/nonEnumLiteral.png)

```
nonEnumLiteral
         ::= stringLiteralRemovingQuotes
           | 'true'
           | 'false'
           | 'null'
           | numberLiteral
           | jsonLiteral
```

referenced by:

* constantDefinition
* jsonArrayLiteral
* jsonField
* literal

**stringLiteralRemovingQuotes:**

![stringLiteralRemovingQuotes](diagram/stringLiteralRemovingQuotes.png)

```
stringLiteralRemovingQuotes
         ::= '"' validSubstring* '"'
```

referenced by:

* nonEnumLiteral

**validSubstring:**

![validSubstring](diagram/validSubstring.png)

```
validSubstring
         ::= '\' [\"'bfnrt]
           | '\u' hex hex hex hex
           | Char - [\"]
```

referenced by:

* stringLiteralRemovingQuotes

**Char:**

![Char](diagram/Char.png)

```
Char     ::= [#x0000-#xFFFF]
```

referenced by:

* validSubstring

**hex:**

![hex](diagram/hex.png)

```
hex      ::= [0-9a-fA-F]
```

referenced by:

* validSubstring

**numberLiteral:**

![numberLiteral](diagram/numberLiteral.png)

```
numberLiteral
         ::= floatingLiteral
           | integerLiteral
```

referenced by:

* nonEnumLiteral

**floatingLiteral:**

![floatingLiteral](diagram/floatingLiteral.png)

```
floatingLiteral
         ::= engNotationLiteral
           | decimalLiteral
```

referenced by:

* numberLiteral

**engNotationLiteral:**

![engNotationLiteral](diagram/engNotationLiteral.png)

```
engNotationLiteral
         ::= ( decimalLiteral | integerLiteral ) [eE] integerLiteral
```

referenced by:

* floatingLiteral

**decimalLiteral:**

![decimalLiteral](diagram/decimalLiteral.png)

```
decimalLiteral
         ::= [-+]? ( ( '0' | [1-9] [0-9]* ) '.' | ( '0' | [1-9] [0-9]* )? '.' [0-9]
                  ) [0-9]*
```

referenced by:

* engNotationLiteral
* floatingLiteral

**integerLiteral:**

![integerLiteral](diagram/integerLiteral.png)

```
integerLiteral
         ::= [-+]? ( '0' | [1-9] [0-9]* )
```

referenced by:

* engNotationLiteral
* numberLiteral

**jsonLiteral:**

![jsonLiteral](diagram/jsonLiteral.png)

```
jsonLiteral
         ::= jsonObjectLiteral
           | jsonArrayLiteral
```

referenced by:

* nonEnumLiteral

**jsonObjectLiteral:**

![jsonObjectLiteral](diagram/jsonObjectLiteral.png)

```
jsonObjectLiteral
         ::= '{' ( jsonField ( ',' jsonField )* )? '}'
```

referenced by:

* jsonLiteral

**jsonField:**

![jsonField](diagram/jsonField.png)

```
jsonField
         ::= simpleIdentifier ':' nonEnumLiteral
```

referenced by:

* jsonObjectLiteral

**jsonArrayLiteral:**

![jsonArrayLiteral](diagram/jsonArrayLiteral.png)

```
jsonArrayLiteral
         ::= '[' ( nonEnumLiteral ( ',' nonEnumLiteral )* )? ']'
```

referenced by:

* jsonLiteral

**parameterDefinition:**

![parameterDefinition](diagram/parameterDefinition.png)

```
parameterDefinition
         ::= description? simpleIdentifier ':' typeSpecification
```

referenced by:

* jsonataDefinition

**jsonataCode:**

![jsonataCode](diagram/jsonataCode.png)

```
jsonataCode
         ::= '{-{' ( string_not_containing - '}-}' ) '}-}'
```

referenced by:

* jsonataDefinition

**singleStep:**

![singleStep](diagram/singleStep.png)

```
singleStep
         ::= simpleIdentifier ( ':' simpleIdentifier )? argumentList?
```

referenced by:

* step

**argumentList:**

![argumentList](diagram/argumentList.png)

```
argumentList
         ::= '(' argument ( ','? argument )* ')'
```

referenced by:

* singleStep

**argument:**

![argument](diagram/argument.png)

```
argument ::= identifier ':' expression
           | simpleIdentifier '::' identifier
```

referenced by:

* argumentList

**identifier:**

![identifier](diagram/identifier.png)

```
identifier
         ::= scopedIdentifier
           | simpleIdentifier
```

referenced by:

* argument

**scopedIdentifier:**

![scopedIdentifier](diagram/scopedIdentifier.png)

```
scopedIdentifier
         ::= simpleIdentifier ( '.' simpleIdentifier )+
```

referenced by:

* identifier

**fork:**

![fork](diagram/fork.png)

```
fork     ::= ( simpleIdentifier ':' )? '[[' forkBranch ( ';' forkBranch )* ']]'
```

referenced by:

* step

**forkBranch:**

![forkBranch](diagram/forkBranch.png)

```
forkBranch
         ::= simpleIdentifier ':' runnable
```

referenced by:

* fork

**conditional:**

![conditional](diagram/conditional.png)

```
conditional
         ::= ( simpleIdentifier ':' )? '[?' conditionalBranch ( ';' conditionalBranch )* '?]'
```

referenced by:

* step

**conditionalBranch:**

![conditionalBranch](diagram/conditionalBranch.png)

```
conditionalBranch
         ::= expression '?' runnable
```

referenced by:

* conditional

**expression:**

![expression](diagram/expression.png)

```
expression
         ::= orExpression
```

referenced by:

* argument
* conditionalBranch

**orExpression:**

![orExpression](diagram/orExpression.png)

```
orExpression
         ::= andExpression ( '||' andExpression )*
```

referenced by:

* expression

**andExpression:**

![andExpression](diagram/andExpression.png)

```
andExpression
         ::= bitOrExpression ( '&&' bitOrExpression )*
```

referenced by:

* orExpression

**bitOrExpression:**

![bitOrExpression](diagram/bitOrExpression.png)

```
bitOrExpression
         ::= bitXorExpression ( '|' bitXorExpression )*
```

referenced by:

* andExpression

**bitXorExpression:**

![bitXorExpression](diagram/bitXorExpression.png)

```
bitXorExpression
         ::= bitAndExpression ( '&' bitAndExpression )*
```

referenced by:

* bitOrExpression

## 
![rr-2.1](diagram/rr-2.1.png) <sup>generated by [RR - Railroad Diagram Generator][RR]</sup>

[RR]: https://www.bottlecaps.de/rr/ui