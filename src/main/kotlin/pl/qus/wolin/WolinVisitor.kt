package pl.qus.wolin

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import pl.qus.wolin.components.*
import pl.qus.wolin.exception.FunctionNotFound
import pl.qus.wolin.exception.RegTypeMismatchException
import java.lang.Exception

enum class Pass {
    DECLARATION, TRANSLATION
}

class WolinVisitor(
    private val nazwaPliku: String,
    pass: Pass,
    private val tokenStream: CommonTokenStream
) : KotlinParserBaseVisitor<WolinStateObject>() {
    val state = WolinStateObject(pass)

    override fun visitFunctionBody(ctx: KotlinParser.FunctionBodyContext): WolinStateObject {
        if (ctx.block() != null)
            ctx.block()?.let {
                visitBlock(it)
            }
        else błędzik(ctx, "Unknown function body type")

        state.fnDeclFreeStackAndRet(state.currentFunction!!)

        state.code("ret")

        return state
    }

    override fun visitControlStructureBody(ctx: KotlinParser.ControlStructureBodyContext): WolinStateObject {
        when {
            ctx.expression() != null -> visitExpression(ctx.expression())
            ctx.block() != null -> visitBlock(ctx.block())
            else -> błędzik(ctx, "Unknown control structure")
        }

        return state
    }

    fun statementProcessor(it: KotlinParser.StatementContext, retVal: Zmienna? = null) {

        // wynik całego przypisania to Unit!

        when {
            // ========================================================
            it.blockLevelExpression() != null -> it.blockLevelExpression()?.let {

                when {
                    it.expression()?.assignmentOperator()?.size != 0 -> {
                        val lewaStrona = it.expression()?.disjunction(0) // disjunction = or
                        val operator = it.expression()?.assignmentOperator(0)
                        val prawaStrona = it.expression()?.disjunction(1)

                        when {
                            operator?.ASSIGNMENT() != null -> {

                                processAssignment(it,
                                    { znajdźSimpleIdW(lewaStrona!!) },
                                    { visitDisjunction(prawaStrona!!) }
                                )

                                state.switchType(Typ.unit, "assignment")

                            }
                            else -> {
                                błędzik(it, "Unknown assignment operator")
                            }
                        }
                    }
                    it.expression() != null -> visitExpression(it.expression())
                    else -> błędzik(it, "Unknown block level expression")
                }

                state.inferTopOperType()

                if (retVal != null) {
                    state.code("let ${state.varToAsm(retVal)} = ${state.currentRegToAsm()} // assign block 'return value' to target variable")
                }
            }
            // ========================================================
            it.declaration() != null -> it.declaration()?.let {
                state.allocReg("for declaration ${it.text.replace("\n", "\\n")}")

                visitDeclaration(it)

                state.inferTopOperType()

                state.freeReg("for declaration ${it.text.replace("\n", "\\n")}")
            }
            // ========================================================
            else -> błędzik(it, "Unknown block level expression")
        }

    }

    private fun znajdźSimpleIdW(lewaStrona: KotlinParser.DisjunctionContext): WolinStateObject {
        val wynik = mutableListOf<ParseTree>()

        //println("lewa strona:${dump(lewaStrona,1)}")

        lewaStrona.traverseFilter(wynik) {
            it is KotlinParser.SimpleIdentifierContext
        }

        val isArray = lewaStrona.any {
            it is KotlinParser.ArrayAccessContext
        }

        if (isArray) {
            val ctx = wynik.first() as KotlinParser.SimpleIdentifierContext

            state.rem(" left side disjunction - prawie dobrze")
            visitDisjunction(lewaStrona)

            state.assignStack.assignLeftSideVar = state.currentReg
            state.assignStack.arrayAssign = true

            return state
        } else {
            val ctx = wynik.first() as KotlinParser.SimpleIdentifierContext

            val zmienna = state.findVarInVariablaryWithDescoping(ctx.Identifier().text)

            // TODO - usunąć assignLeftSideVar
            //state.currentReg = zmienna
            state.assignStack.assignLeftSideVar = zmienna

            state.switchType(zmienna.type, "by znajdźSimpleIdW")

            return state
        }
    }


    override fun visitBlock(ctx: KotlinParser.BlockContext): WolinStateObject {
        state.currentScopeSuffix += "."

        val blockValue = state.allocReg("for block 'return value' ${ctx.text.replace("\n", "\\n")}")

        ctx.statements()?.statement()?.forEach {
            statementProcessor(it)
        }

        if(state.assignStack.isNotEmpty() && state.pass == Pass.TRANSLATION && !blockValue.type.isUnit) {
//            state.code("// ${state.varToAsm(state.assignLeftSideVar!!)} = ${state.varToAsm(blockValue)} visitBlock")
            checkTypeAndAddAssignment(ctx, state.assignStack.assignRightSideFinalVar, blockValue, "visit block")
        }

        state.freeReg(
            "for block 'return value' ${ctx.text.replace(
                "\n",
                "\\n"
            )}, type = ${state.currentWolinType}"
        )

        state.currentScopeSuffix = state.currentScopeSuffix.dropLast(1)

        return state
    }

    override fun visitExpression(ctx: KotlinParser.ExpressionContext): WolinStateObject {
        when {
            ctx.assignmentOperator().size == 1 -> {
                when {
                    ctx.assignmentOperator().first().ASSIGNMENT() != null -> {
                        processAssignment(ctx,
                            { znajdźSimpleIdW(ctx.disjunction(0)) /*visitDisjunction(ctx.disjunction(0)) */ },
                            { visitDisjunction(ctx.disjunction(1)) })
                    }
                    else -> błędzik(ctx, "Unknown assignment operator")
                }
                //visitAssignmentOperator(ctx.assignmentOperator().first())
            }
            ctx.assignmentOperator().size > 1 -> błędzik(ctx, "don't know how to handle assignment operator >1")
            ctx.disjunction().size == 1 -> ctx.disjunction().firstOrNull()?.let {
                visitDisjunction(it)
            }
            else -> błędzik(ctx, "Unknown expression")
        }

        return state
    }


    override fun visitDisjunction(ctx: KotlinParser.DisjunctionContext): WolinStateObject {
        // nie ma or
        if (ctx.conjunction()?.size == 1)
            ctx.conjunction()?.firstOrNull()?.let {
                visitConjunction(it) // conjunction = and*
            }
        else {
            processTypePreservingOp(
                { visitConjunction(ctx.conjunction(0)) },
                { visitConjunction(ctx.conjunction(1)) },
                simpleResultFunction("or", Typ.bool),
                "or operation"
            )
        }

        return state
    }

    override fun visitConjunction(ctx: KotlinParser.ConjunctionContext): WolinStateObject {
        if (ctx.equalityComparison()?.size == 1)
            ctx.equalityComparison()?.firstOrNull()?.let {
                visitEqualityComparison(it)
            }
        else {
            processTypePreservingOp(
                { visitEqualityComparison(ctx.equalityComparison(0)) },
                { visitEqualityComparison(ctx.equalityComparison(1)) },
                simpleResultFunction("and", Typ.bool),
                "and operation"
            )
        }
        return state
    }

    override fun visitEqualityComparison(ctx: KotlinParser.EqualityComparisonContext): WolinStateObject {
        when {
            ctx.comparison().size == 1 -> ctx.comparison().firstOrNull()?.let {
                visitComparison(it)
            }
            ctx.equalityOperation().size > 1 -> błędzik(ctx, "equality op size > 1")
            ctx.equalityOperation().size == 1 -> {

                val oper = when (ctx.equalityOperation(0).text) {
                    "==" -> "evaleq"
                    "!=" -> "evalneq"
                    "<" -> "evallt"
                    ">" -> "evalgt"
                    "<=" -> "evaleqlt"
                    ">=" -> "evaleqgt"
                    else -> "unknown eq"
                }

                processTypeChangingOp(
                    { visitComparison(ctx.comparison(0)) },
                    { visitComparison(ctx.comparison(1)) },
                    { akt, lewy, prawy ->

                        state.code(
                            "$oper ${state.varToAsm(akt)} = ${state.varToAsm(lewy)}, ${state.varToAsm(
                                prawy
                            )} // two sides"
                        )

                        Typ.bool
                    },
                    "equality check: $oper"
                )
            }
            else -> błędzik(ctx, "Unknown eq comparison")
        }

        return state
    }

    override fun visitComparison(ctx: KotlinParser.ComparisonContext): WolinStateObject {
        when {
            ctx.namedInfix().size == 1 -> visitNamedInfix(ctx.namedInfix().first())
            ctx.comparisonOperator() != null -> {
                val oper = ctx.comparisonOperator()!!

                when {
                    oper.GE() != null -> {
                        processTypeChangingOp(
                            { visitNamedInfix(ctx.namedInfix(0)) },
                            { visitNamedInfix(ctx.namedInfix(1)) },
                            { wynik, lewa, prawa ->
                                state.code(
                                    "evalgteq ${state.varToAsm(wynik)} = ${state.varToAsm(lewa)}, ${state.varToAsm(
                                        prawa
                                    )}"
                                )
                                Typ.bool
                            },
                            "for >="
                        )
                    }
                    oper.LE() != null -> {
                        processTypeChangingOp(
                            { visitNamedInfix(ctx.namedInfix(0)) },
                            { visitNamedInfix(ctx.namedInfix(1)) },
                            { wynik, lewa, prawa ->
                                state.code(
                                    "evalgteq ${state.varToAsm(wynik)} = ${state.varToAsm(prawa)}, ${state.varToAsm(
                                        lewa
                                    )}"
                                )
                                Typ.bool
                            },
                            "for <="
                        )
                    }
                    oper.LANGLE() != null -> {
                        processTypeChangingOp(
                            { visitNamedInfix(ctx.namedInfix(0)) },
                            { visitNamedInfix(ctx.namedInfix(1)) },
                            { wynik, lewa, prawa ->
                                state.code(
                                    "evalless ${state.varToAsm(wynik)} = ${state.varToAsm(lewa)}, ${state.varToAsm(
                                        prawa
                                    )}"
                                )
                                Typ.bool
                            },
                            "for <"
                        )
                    }
                    oper.RANGLE() != null -> {
                        processTypeChangingOp(
                            { visitNamedInfix(ctx.namedInfix(0)) },
                            { visitNamedInfix(ctx.namedInfix(1)) },
                            { wynik, lewa, prawa ->
                                state.code(
                                    "evalless ${state.varToAsm(wynik)} = ${state.varToAsm(prawa)}, ${state.varToAsm(
                                        lewa
                                    )}"
                                )
                                Typ.bool
                            },
                            "for >"
                        )
                    }
                    else ->
                        błędzik(ctx, "Unknown comparison 2")

                }
            }
            else -> błędzik(ctx, "Unknown comparison")
        }

        return state
    }

    override fun visitEqualityOperation(ctx: KotlinParser.EqualityOperationContext): WolinStateObject {
        println("")

        state.rem(" tu porównanie")

        return state
    }


    override fun visitNamedInfix(ctx: KotlinParser.NamedInfixContext): WolinStateObject {
        when {
            ctx.inOperator().size > 0 -> błędzik(ctx, "In operator size > 0")
            ctx.elvisExpression().size == 1 -> ctx.elvisExpression().firstOrNull()?.let {
                visitElvisExpression(it)
            }
            else -> błędzik(ctx, "Unknown named infix")
        }

        return state
    }

    override fun visitElvisExpression(ctx: KotlinParser.ElvisExpressionContext): WolinStateObject {
        when {
            ctx.ELVIS().size > 0 -> błędzik(ctx, "elvis op size > 0")
            ctx.infixFunctionCall().size == 1 -> ctx.infixFunctionCall().firstOrNull()?.let {
                visitInfixFunctionCall(it)
            }
            else -> błędzik(ctx, "unknown Elvis expression")
        }

        return state
    }

    override fun visitInfixFunctionCall(ctx: KotlinParser.InfixFunctionCallContext): WolinStateObject {
        when {
            ctx.rangeExpression().size == 1 -> ctx.rangeExpression().firstOrNull()?.let {
                visitRangeExpression(it)
            }
            ctx.simpleIdentifier().size > 0 -> błędzik(ctx, "Unknown infix function call")
            else -> błędzik(ctx, "Unknown infix function call")
        }

        return state
    }

    override fun visitRangeExpression(ctx: KotlinParser.RangeExpressionContext): WolinStateObject {
        when {
            ctx.RANGE().size > 0 -> błędzik(ctx, "range op size > 0")
            ctx.additiveExpression().size == 1 -> ctx.additiveExpression().firstOrNull()?.let {
                visitAdditiveExpression(it)
            }
            else -> błędzik(ctx, "Unknown range expression")
        }
        return state
    }

    override fun visitAdditiveExpression(ctx: KotlinParser.AdditiveExpressionContext): WolinStateObject {
        when {
            ctx.additiveOperator().size > 0 -> {

                try {
                    processTypePreservingOp(
                        { visitMultiplicativeExpression(ctx.multiplicativeExpression(0)) },
                        { visitMultiplicativeExpression(ctx.multiplicativeExpression(1)) },
                        simpleResultFunction("add"),
                        "adding operator"
                    )
                } catch (ex: RegTypeMismatchException) {
                    błędzik(
                        ctx,
                        "Type mismatch: ${ctx.multiplicativeExpression(0).text} can't be added to ${ctx.multiplicativeExpression(
                            1
                        ).text}"
                    )
                }
            }
            ctx.multiplicativeExpression().size == 1 -> ctx.multiplicativeExpression()?.firstOrNull()?.let {
                visitMultiplicativeExpression(it)
            }
            else -> błędzik(ctx, "Unknown additiv expression")
        }
        return state
    }

    override fun visitMultiplicativeExpression(ctx: KotlinParser.MultiplicativeExpressionContext): WolinStateObject {
        when {
            ctx.multiplicativeOperation().size > 0 -> {
                val op = ctx.multiplicativeOperation(0)
                when {
                    op?.MULT() != null -> {
                        processTypePreservingOp(
                            { visitPrefixUnaryExpression(ctx.typeRHS(0).prefixUnaryExpression(0)) },
                            { visitPrefixUnaryExpression(ctx.typeRHS(1).prefixUnaryExpression(0)) },
                            simpleResultFunction("mul"),
                            "multiplication"
                        )
                    }
                    op?.DIV() != null -> {
                        processTypePreservingOp(
                            { visitPrefixUnaryExpression(ctx.typeRHS(0).prefixUnaryExpression(0)) },
                            { visitPrefixUnaryExpression(ctx.typeRHS(1).prefixUnaryExpression(0)) },
                            simpleResultFunction("div"),
                            "division"
                        )
                    }
                    op?.MOD() != null -> {
                        processTypePreservingOp(
                            { visitPrefixUnaryExpression(ctx.typeRHS(0).prefixUnaryExpression(0)) },
                            { visitPrefixUnaryExpression(ctx.typeRHS(1).prefixUnaryExpression(0)) },
                            simpleResultFunction("mod"),
                            "modulo"
                        )
                    }
                    else -> błędzik(ctx, "Unknown multiplicative op")
                }
            }
            ctx.typeRHS().size == 1 -> ctx.typeRHS().firstOrNull()?.let {
                visitTypeRHS(it)
            }
            else -> błędzik(ctx, "Don't know how to process multiplicativeExpression")
        }
        return state
    }

//    override fun visitAssignmentOperator(ctx: KotlinParser.AssignmentOperatorContext): WolinStateObject {
//        when {
//            ctx.ASSIGNMENT() != null -> {
//                println("tu!")
//            }
//            else -> {
//                błędzik(ctx, "Unknown assignment operaor")
//            }
//        }
//
//        return state
//    }

    fun processAssignment(ctx: ParseTree, leftFunction: () -> WolinStateObject, rightFunction: () -> WolinStateObject) {

        state.assignStack.push(AssignEntry())

        state.rem(" lewa strona assignment") // TODO użyć tego rejestru zamiast assignLeftSideVar
        val leftSide = state.allocReg("For assignment left side")

        leftFunction()
        state.inferTopOperType() // aktualny typ jest ustawiony źle! To musi być wina prawej funkcji!

        val destinationReg = state.assignStack.assignLeftSideVar
        val destinationType = destinationReg.type

        //state.currentReg.type.isPointer = true
        //state.inferTopOregType()

        state.rem(" prawa strona assignment")

        val tempSourceReg = state.allocReg("for value that gets assigned to left side")
        state.assignStack.assignRightSideFinalVar = tempSourceReg
        rightFunction()

        //state.inferTopOregType() // aktualny typ jest ustawiony źle! To musi być wina prawej funkcji!

        tempSourceReg.type = destinationType.arrayElementType // to ustawia źle aktualny typ...
        tempSourceReg.type.isPointer = false // przy podstawieniu konkretnej wartości
        tempSourceReg.type.array = false

//        val sourceReg = state.findVarInVariablaryWithDescoping(tempSourceReg.name)
//        if (!state.canBeAssigned(sourceReg.type, destinationReg.type) && state.pass == Pass.TRANSLATION) {
//            throw TypeMismatchException("Attempt to assign ${tempSourceReg.name}:${sourceReg.type} to variable of type ${destinationReg.name}:${destinationReg.type}")
//        }

        checkTypeAndAddAssignment(ctx, destinationReg, tempSourceReg, "process assignment")

        if (state.assignStack.arrayAssign) {
            // też niepotrzebne
            //state.code("let ${state.varToAsm(leftSide)} = ${state.varToAsm(tempSourceReg)} // ONLY FOR NON-TRIVIAL LEFT SIDE ASSIGN")
            state.assignStack.arrayAssign = false
        }
        state.freeReg("for value that gets assigned to left side, type = ${state.currentWolinType}")

        state.freeReg("For assignment left side")

        state.assignStack.pop()
    }

    override fun visitTypeRHS(ctx: KotlinParser.TypeRHSContext): WolinStateObject {
        when {
            ctx.prefixUnaryExpression().size == 1 -> ctx.prefixUnaryExpression().firstOrNull()?.let {
                visitPrefixUnaryExpression(it)
            }
            ctx.typeOperation().size > 0 -> {
                błędzik(ctx, "There's a type in second prefixUnaryExpression - obsłużyć!!!")
                /*
             TypeRHSContext
             PrefixUnaryExpressionContext
              PostfixUnaryExpressionContext
               AtomicExpressionContext
                LiteralConstantContext
                 2<<<--- TERMINAL
             TypeOperationContext
              :<<<--- TERMINAL
             PrefixUnaryExpressionContext
              PostfixUnaryExpressionContext
               AtomicExpressionContext
                SimpleIdentifierContext
                 ubyte<<<--- TERMINAL

                 */
            }
            else -> błędzik(ctx, "Unknown TypeRHS")
        }
        return state
    }

    override fun visitPrefixUnaryExpression(ctx: KotlinParser.PrefixUnaryExpressionContext): WolinStateObject {
        when {
            ctx.prefixUnaryOperation().size > 0 -> błędzik(
                ctx,
                "Nie umiem wykonać prefix unary operation dla '${ctx.text}'"
            )
            ctx.postfixUnaryExpression() != null -> visitPostfixUnaryExpression(ctx.postfixUnaryExpression())
            else -> błędzik(ctx, "Unknown prefix unary exception")
        }

        return state
    }

    override fun visitPostfixUnaryExpression(ctx: KotlinParser.PostfixUnaryExpressionContext): WolinStateObject {
        val atomEx = ctx.atomicExpression()
        val callableRef = ctx.callableReference()
        val postfixOp = ctx.postfixUnaryOperation()

        when {
            callableRef != null -> błędzik(ctx, "Nie wiem co się robi z callable ref")
            postfixOp.size > 1 -> błędzik(ctx, "Za dużo postfixUnaryOperation w postfixUnaryExpression")
            postfixOp.size == 0 && atomEx != null -> visitAtomicExpression(atomEx)
            atomEx != null && postfixOp != null -> {
                val operator = postfixOp.first()

                when {
                    operator.callSuffix() != null -> {
                        val procName =
                            atomEx.simpleIdentifier()?.Identifier()?.text ?: throw Exception("Pusta nazwa procedury")

                        if (postfixOp.size > 1)
                            błędzik(ctx, "Za dużo postfixUnaryOp w postifxOp")

                        val postfixUnaryOperation = postfixOp.first()
                        val callSuffix = postfixUnaryOperation.callSuffix()
                        val arguments = callSuffix.valueArguments()

                        val prototyp = try {
                            val klasa = state.findClass(procName)
                            // TODO - tymczasowo dodać do funkcjarium konstruktor bez parametrów przy rejestrowaniu klasy
                            // konstruktor zawiera allokację i zwraca adres

                            state.functiary.first { it.fullName == klasa.name }
                        } catch (ex: StringIndexOutOfBoundsException) {
                            try {
                                state.findProc(procName)
                            } catch (ex: FunctionNotFound) {
                                state.lambdaTypeToFunction(state.findVarInVariablaryWithDescoping(procName))
                            }
                        }

                        if (prototyp.arguments.size != arguments.valueArgument()?.size)
                            błędzik(ctx, "Wrong number of arguments in function call $procName")

                        state.switchType(prototyp.type, "function type 1")
                        state.inferTopOperType()

                        state.fnCallAllocRetAndArgs(prototyp)

                        if (prototyp.arguments.any { it.allocation == AllocType.FIXED && it.location == "CPU.X" }) {
                            state.code("save CPU.X // save SP, as X is used by native call argument")
                        }

                        arguments.valueArgument()?.forEachIndexed { i, it ->
                            state.rem(" obsługa argumentu $i wywołania $procName")
                            state.allocReg("for call argument $i", prototyp.arguments[i].type)

                            // w valueArgumentContext jest ExpressionContext
                            visitValueArgument(it)

                            if (prototyp.arguments[i].allocation == AllocType.FIXED) {
                                // dla argumentów CPU koelejność musi być A, Y, X!!!
                                state.code(
                                    "let ${prototyp.arguments[i].location}${prototyp.arguments[i].typeForAsm} = ${state.currentRegToAsm()}"
                                )
                                if (prototyp.arguments[i].location?.startsWith("CPU.") == true)
                                    state.code("save ${prototyp.arguments[i].location}")
                            } else {
                                val found = state.findStackVector(state.callStack, prototyp.arguments[i].name)

                                state.code(
                                    "let SPF(${found.first})${found.second.typeForAsm} = ${state.currentRegToAsm()}"
                                )
                            }

                            state.freeReg("for call argument $i, type = ${prototyp.arguments[i].type}")
                        }

                        state.switchType(prototyp.type, "function type 2")

                        (prototyp.arguments.size - 1 downTo 0).forEach {
                            val arg = prototyp.arguments[it]

                            if (arg.allocation == AllocType.FIXED && arg.location?.startsWith("CPU.") == true) {
                                state.code("restore ${arg.location} // fill register for call")
                            }
                        }

                        state.code(state.getFunctionCallCode(procName))

                        if (prototyp.arguments.any { it.allocation == AllocType.FIXED && it.location == "CPU.X" }) {
                            state.code("restore CPU.X // restore SP, as X is used by native call")
                        }

                        state.fnCallReleaseArgs(prototyp)

                        if (!prototyp.type.isUnit)
                            state.code("let ${state.currentRegToAsm()} = ${state.varToAsm(state.callStack.peek())}// copy return parameter - TODO sprawdzić co jeśli wywołanie funkcji było bez podstawienia!!!")

                        state.fnCallReleaseRet(prototyp)

                        state.code("free SPF <${prototyp.type.type}>, #${prototyp.type.sizeOnStack}")

                        if (state.currentClass != null)
                            state.code("setup HEAP = this")

                    }
                    operator.INCR() != null -> {
                        when {
                            atomEx.simpleIdentifier() != null -> {
                                val identifier = atomEx.simpleIdentifier()?.Identifier()?.text
                                    ?: throw Exception("No identifier for ++")
                                val zmienna = state.findVarInVariablaryWithDescoping(identifier)
                                state.code(
                                    "add ${state.varToAsm(zmienna)} = ${state.varToAsm(
                                        zmienna
                                    )}, #1${zmienna.typeForAsm} // simple id"
                                )
                                state.switchType(zmienna.type, "++ operator")
                            }
                            atomEx.literalConstant() != null -> {

                                parseLiteralConstant(state.currentReg, atomEx.literalConstant())

                                state.code("add ${state.currentRegToAsm()} = #${state.currentReg.immediateValue}${state.currentReg.typeForAsm}, #1${state.currentReg.typeForAsm}, add // literal const")
                                //state.currentReg.type = "Ubyte"
                            }
                            else -> błędzik(atomEx, "Nie wiem jak obsłużyć")
                        }
                    }
                    operator.DECR() != null -> {
                        when {
                            atomEx.simpleIdentifier() != null -> {
                                val identifier = atomEx.simpleIdentifier()?.Identifier()?.text
                                    ?: throw Exception("No identifier for --")
                                val zmienna = state.findVarInVariablaryWithDescoping(identifier)
                                state.code(
                                    "sub ${state.varToAsm(zmienna)} = ${state.varToAsm(
                                        zmienna
                                    )}, #1${zmienna.typeForAsm} // simple id"
                                )
                                state.switchType(zmienna.type, "-- operator")
                            }
                            atomEx.literalConstant() != null -> {

                                parseLiteralConstant(state.currentReg, atomEx.literalConstant())

                                state.code("sub ${state.currentRegToAsm()} = #${state.currentReg.immediateValue}${state.currentReg.typeForAsm}, #1${state.currentReg.typeForAsm}, add // literal const")
                                //state.currentReg.type = "Ubyte"
                            }
                            else -> błędzik(atomEx, "Nie wiem jak obsłużyć")
                        }
                    }
                    ctx.postfixUnaryOperation().size > 1 -> błędzik(ctx, "too many postfix unary ops")
                    ctx.postfixUnaryOperation().size == 1 -> {

                        val lewo = atomEx
                        val prawo = ctx.postfixUnaryOperation()[0]

                        when {
                            prawo.memberAccessOperator() != null -> {

                                state.rem(" lewa strona deref")
                                visitAtomicExpression(lewo)
                                state.rem(" sprawdzic, czy aktualny typ to klasa")
                                state.rem(" jesli tak, to na gorze heapu jest uniqid klasy")

                                // TODO tu podstawiamy pod aktualny rejestr instancje jakiejś klasy
                                // ale nie wiemy co to za klasa,  bo to już tylko ptr
                                // więc nie wiemy jakie ma metody ten ptr!

                                state.rem(" prawa strona deref")
                                //visitPostfixUnaryOperation(poKropce)
                                //błędzik(ctx,"nie wiem jak dereferncić")

                                val reg = state.currentReg
                                val safeDeref = prawo.memberAccessOperator().QUEST() != null

                                val costam = prawo.postfixUnaryExpression()
                                if (safeDeref) {
                                    state.code("evaleq costam[bool] = $reg[ptr], null")
                                    state.code("beq costam[bool] = #0, jakis_label[adr]")
                                }

                                state.rem(" postfix unary w dereferencji")
                                //visitPostfixUnaryExpression(costam)
                            }
                            prawo.arrayAccess() != null -> {
                                // ARRAYCODE
                                val currEntReg = state.currentReg
                                state.arrayElementSize = currEntReg.type.arrayElementSize

                                // jeśli indeks 8-bitowy i element nie robic tej allokacji

                                state.codeOn = false
                                visitAtomicExpression(lewo)
                                state.codeOn = true

                                state.allocReg("arr_deref", state.currentWolinType).apply {
                                    type.isPointer = true
                                }
                                state.rem(" LEWA strona array access, czyli co to za zmienna")
                                visitAtomicExpression(lewo)
                                state.rem(" PRAWA strona array access, czyli indeks w nawiasach")
                                visitArrayAccess(prawo.arrayAccess())

                                state.rem(" kod obsługi tablicy")

                                when {
                                    state.currentShortArray == null -> {
                                        state.rem(" non-fast array")
                                        state.code("let ${state.varToAsm(currEntReg)} = ${state.varToAsmNoType(state.currentReg)}[ptr]")
                                    }
                                    state.currentShortArray != null && state.currentShortArray!!.allocation == AllocType.NORMAL -> {
                                        state.rem(" allocated fast array")
                                        state.code("let ${state.varToAsm(currEntReg)} = ${state.currentShortArray!!.name}[ptr], ${state.currentRegToAsm()}")
                                        state.currentShortArray = null
                                    }
                                    state.currentShortArray != null && state.currentShortArray!!.allocation == AllocType.FIXED -> {
                                        state.rem(" allocated fast array")
                                        state.code("let ${state.varToAsm(currEntReg)} = ${state.currentShortArray!!.location}[ptr], ${state.currentRegToAsm()}")
                                        state.currentShortArray = null
                                    }

                                    else -> throw Exception("Dereference of unknown array!")
                                }

                                // jeśli indeks 8-bitowy i element nie robic tej allokacji
                                state.freeReg("arr_deref")
                            }
                            else -> błędzik(ctx, "Nieznany postfixUnaryOp")
                        }
                    }
                    else -> błędzik(ctx, "Nieznana prawa strona ${operator.javaClass.simpleName}")
                }
            }
            else -> błędzik(ctx, "null po którejś stronie postfixUnaryExpression")
        }

        return state
    }

    override fun visitArrayAccess(ctx: KotlinParser.ArrayAccessContext): WolinStateObject {
        when {
            ctx.expression().size == 1 -> {
                val prevReg = state.currentReg!!

                // ARRAYCODE

                if (state.currentShortArray == null) {
                    state.allocReg("For calculating index", state.currentWolinType)
                }

                visitExpression(ctx.expression(0))

                if (state.currentShortArray == null)
                    state.forceTopOregType(Typ.uword) // typ indeksu
                else
                    state.forceTopOregType(Typ.ubyte) // typ indeksu


                // ARRAYCODE
                if (state.currentShortArray == null) {
                    when {
                        state.arrayElementSize > 1 -> {
                            state.rem(" long index, multi-byte per element array")
                            state.code("mul ${state.currentRegToAsm()} = ${state.currentRegToAsm()}, #${state.arrayElementSize}")
                            state.code("add ${state.varToAsm(prevReg)} = ${state.varToAsm(prevReg)}, ${state.currentRegToAsm()}")
                        }
                        else -> {
                            state.rem(" long index, single byte per element array")
                            state.code("add ${state.varToAsm(prevReg)} = ${state.varToAsm(prevReg)}, ${state.currentRegToAsm()}")
                        }
                    }

                    state.freeReg("For calculating index")

                } else {
                    state.rem(" fast array - no additional op")
                    //state.code("add ${state.varToAsm(prevReg)} = ${state.varToAsm(prevReg)}, ${state.currentRegToAsm()}")
                }

                state.rem(" after index")

                state.rem("dereference value at topRegister")
            }
            else -> błędzik(ctx, "wrong number of expressions for array deref")
        }
        return state
    }

//    override fun visitMemberAccessOperator(ctx: KotlinParser.MemberAccessOperatorContext): WolinStateObject {
//        when {
//            ctx.DOT() != null -> {
//                val cos = state.currentReg
//                println("tu!")
//            }
//            ctx.QUEST() != null -> {
//                błędzik(ctx, "Nie wiem jak obsłużyć ?:")
//            }
//        }
//
//        return state
//    }

    override fun visitValueArgument(ctx: KotlinParser.ValueArgumentContext): WolinStateObject {
        state.rem("Prze visit vALUE")

        when {
            ctx.ASSIGNMENT() != null -> błędzik(ctx, "Nie wiem jak obsłużyć assignment v value argument")
            ctx.MULT() != null -> błędzik(ctx, "Nie wiem jak obsłużyć mult v value argument")
            ctx.simpleIdentifier() != null -> błędzik(ctx, "Nie wiem jak obsłużyć simpleId v value argument")
            ctx.expression() != null -> {
                state.rem(" obliczenia dla parametru ${ctx.expression().text}")
                visitExpression(ctx.expression())
            }
        }

        state.rem("po visit value")

        return state
    }

    override fun visitAtomicExpression(ctx: KotlinParser.AtomicExpressionContext): WolinStateObject {
        when {
            ctx.simpleIdentifier() != null -> visitSimpleIdentifier(ctx.simpleIdentifier())
            ctx.literalConstant() != null -> {
                val wartość = Zmienna(allocation = AllocType.LITERAL, stack = "")
                parseLiteralConstant(wartość, ctx.literalConstant())
                state.code("let ${state.currentRegToAsm()} = ${state.varToAsm(wartość)} // atomic ex")
            }
            ctx.conditionalExpression() != null -> visitConditionalExpression(ctx.conditionalExpression())
            ctx.jumpExpression() != null -> {
                visitJumpExpression(ctx.jumpExpression())
            }
            ctx.tryExpression() != null -> {
                visitTryExpression(ctx.tryExpression())
            }
            ctx.loopExpression() != null -> {
                visitLoopExpression(ctx.loopExpression())
            }
            ctx.functionLiteral() != null -> {
                visitFunctionLiteral(ctx.functionLiteral())
            }
            ctx.thisExpression() != null -> {
                visitThisExpression(ctx.thisExpression())
            }
            else -> błędzik(ctx, "Unknown AtomicExpression")
        }

        return state
    }

    override fun visitThisExpression(ctx: KotlinParser.ThisExpressionContext): WolinStateObject {
        when {
            ctx.LabelReference() != null -> błędzik(ctx, "Don't know how to process this@xxxx")
            ctx.THIS() != null -> {
                val zmienna = state.findVarInVariablaryWithDescoping("this")

                state.allocReg("for statement this")

                checkTypeAndAddAssignment(ctx, state.currentReg, zmienna, "this expression")

            }
            else -> błędzik(ctx, "Uknown this reference!")
        }

        return state
    }

    override fun visitFunctionLiteral(ctx: KotlinParser.FunctionLiteralContext): WolinStateObject {
        val parametryLambdy = ctx.lambdaParameters()

        val blok = ctx.statements()

        val lambdaName = "lambda_function_${state.lambdaCounter++}"

        val nowaFunkcja = Funkcja(fullName = lambdaName)

        // TODO - dodać do skołpu zmienne tej lambdy

        val rememberedFunction = state.currentFunction

        state.currentFunction = nowaFunkcja

        parametryLambdy.lambdaParameter()?.forEachIndexed { index, it ->
            when {
                it.multiVariableDeclaration() != null -> błędzik(ctx, "Nie wiem jak obsłużyć multiVarDecl w lambdzie")
                it.variableDeclaration() != null -> {
                    val decl = it.variableDeclaration()
                    val typ = decl.type()
                    val id = decl.simpleIdentifier().text

                    val arg = if (typ == null) {
                        // TODO - parametr lambdy może być podany bez typu, w takim wypadku musimy go sobie pobrać
                        // z typu zmiennej, do której jest przypisywana ta lambda (assignLeftSideVar)

                        if (state.assignStack.isNotEmpty()) {
                            val funkcja = state.lambdaTypeToFunction(state.assignStack.assignLeftSideVar)
                            val interred = funkcja.arguments[index]
                            state.createAndRegisterVar(id, AllocType.NORMAL, interred.type, true, "SPF")
                        } else {
                            błędzik(ctx, "Can't infer type of lambda!")
                            throw Exception()
                        }
                    } else {
                        state.createAndRegisterVar(id, typ, null, true, "SPF")
                    }

                    if (arg.allocation != AllocType.FIXED) nowaFunkcja.arguments.add(arg)
                }
                else -> {
                    błędzik(ctx, "Może nie błąd, tylko lambda bez parametrów?")
                }
            }
        }

        state.codeOn = false

        state.fnCallAllocRetAndArgs(nowaFunkcja)
        visitStatements(blok)
        state.fnCallReleaseArgs(nowaFunkcja)
        state.fnCallReleaseRet(nowaFunkcja)

        state.codeOn = true

        nowaFunkcja.type = state.currentWolinType

        nowaFunkcja.lambdaBody = blok

        state.currentFunction = rememberedFunction

        state.functiary.add(nowaFunkcja)

        val test = state.functionToLambdaType(nowaFunkcja)

        state.switchType(test, "lambda declaration")
        state.inferTopOperType()

        state.code("let ${state.currentRegToAsm()} = ${nowaFunkcja.labelName}[adr]")

        return state
    }

    override fun visitLoopExpression(ctx: KotlinParser.LoopExpressionContext): WolinStateObject {
        when {
            ctx.whileExpression() != null -> visitWhileExpression(ctx.whileExpression())
            ctx.doWhileExpression() != null -> visitDoWhileExpression(ctx.doWhileExpression())
            //ctx.forExpression()
            else -> błędzik(ctx, "Unknown loop expression")
        }

        return state
    }

    override fun visitWhileExpression(ctx: KotlinParser.WhileExpressionContext): WolinStateObject {

        state.loopCounter++

        val condLabel = labelMaker("loopStart", state.loopCounter)

        val afterBodyLabel = labelMaker("loopEnd", state.loopCounter)

        state.code("label $condLabel")

        state.forceTopOregType(Typ.bool)

        visitExpression(ctx.expression())

        state.code("bne ${state.currentRegToAsm()} = #1[bool], $afterBodyLabel<label_po_if>[uword]")

        visitControlStructureBody(ctx.controlStructureBody())

        state.code("goto $condLabel[adr]")

        state.code("label $afterBodyLabel")

        return state
    }

    override fun visitDoWhileExpression(ctx: KotlinParser.DoWhileExpressionContext): WolinStateObject {

        state.loopCounter++

        val bodyLabel = labelMaker("loopStart", state.loopCounter)

        val afterBodyLabel = labelMaker("loopEnd", state.loopCounter)

        state.code("label $bodyLabel")

        state.forceTopOregType(Typ.bool)

        visitControlStructureBody(ctx.controlStructureBody())

        visitExpression(ctx.expression())

        state.code("beq ${state.currentRegToAsm()} = #1[bool], $bodyLabel<label_po_if>[uword]")

        state.code("label $afterBodyLabel")

        return state
    }

    override fun visitConditionalExpression(ctx: KotlinParser.ConditionalExpressionContext): WolinStateObject {
        when {
            ctx.ifExpression() != null -> visitIfExpression(ctx.ifExpression())
            ctx.whenExpression() != null -> visitWhenExpression(ctx.whenExpression())
            else -> błędzik(ctx, "Unknown conditional")
        }

        return state
    }

    override fun visitWhenExpression(ctx: KotlinParser.WhenExpressionContext): WolinStateObject {
        state.rem("When expression start")


        if (ctx.expression() != null) {
            val resultReg = state.allocReg("for evaluating when top expression")
            state.simpleWhen = false
            visitExpression(ctx.expression())
            state.inferTopOperType()
        } else {
            state.simpleWhen = true
        }

        val resultReg = state.allocReg("for bool result of each condition", Typ.bool)
        //val condReg = state.allocReg("for evaluating each when branch condition") // TODO - dostaje zły typ!

        val valueForAssign = state.allocReg("for value if when assigned")

        if(state.assignStack.isNotEmpty())
            valueForAssign.type = state.assignStack.assignLeftSideVar.type


//        if (!state.simpleWhen)
//            condReg.type = state.currentWolinType

        // TODO - przy podstawieniu wymysić else!

        if (ctx.whenEntry().size > 0) {
            state.lastWhenEntry = false
            ctx.whenEntry()?.dropLast(1)?.forEach {
                state.rem("normal when condition")
                visitWhenEntry(it)
            }
            state.lastWhenEntry = true
            ctx.whenEntry()?.last()?.let {
                state.rem("last when condition")
                visitWhenEntry(it)
            }
        }
        state.rem("When expression end")

        state.code("label ${labelMaker("whenEndLabel", state.whenCounter++)}")

        if(state.assignStack.isNotEmpty())
            checkTypeAndAddAssignment(ctx, state.assignStack.assignRightSideFinalVar, valueForAssign, "when assignment")

        state.freeReg("for value if when assigned")

        //state.freeReg("for evaluating each when branch condition")
        state.freeReg("for bool result of each condition")

        if (!state.simpleWhen)
            state.freeReg("for evaluating when top expression")


        return state
    }

    override fun visitWhenEntry(ctx: KotlinParser.WhenEntryContext): WolinStateObject {
        val kondycje = ctx.whenCondition()

        val resultReg = state.regFromTop(2)

        val boolReg = state.regFromTop(1)
        val condReg = state.regFromTop(0)


        state.rem("warunek")
        val whenLabel = labelMaker("whenLabel", state.labelCounter++)
        val nextLabel = labelMaker("whenLabel", state.labelCounter)

        state.code("label $whenLabel")

        kondycje?.firstOrNull()?.let {
            visitWhenCondition(it)
        }

        if (state.simpleWhen)
            state.inferTopOperType()
//        else
//            state.forceTopOregType(resultReg.type)

        if (ctx.ELSE() != null) {
            state.rem("when else branch")
        } else if (state.simpleWhen)
            if (state.lastWhenEntry)
                state.code(
                    "bne ${state.varToAsm(resultReg)} = #1[bool], ${labelMaker(
                        "whenEndLabel",
                        state.whenCounter
                    )}[adr]"
                ) // TODO lub whenEndLabel dla ostatniego
            else
                state.code("bne ${state.varToAsm(resultReg)} = #1[bool], ${nextLabel}[adr]") // TODO lub whenEndLabel dla ostatniego
        else {
            state.code("evaleq ${state.varToAsm(boolReg)} = ${state.varToAsm(resultReg)}, ${state.varToAsm(condReg)}") // TODO tylko jeśłi to when z wyrażeniem!
            if (state.lastWhenEntry)
                state.code(
                    "bne ${state.varToAsm(boolReg)} = #1[bool], ${labelMaker(
                        "whenEndLabel",
                        state.whenCounter
                    )}[adr]"
                ) // TODO lub whenEndLabel dla ostatniego
            else
                state.code("bne ${state.varToAsm(boolReg)} = #1[bool], ${nextLabel}[adr]")
        }

        state.rem("when operacja")

        val whenTrue = ctx.controlStructureBody()
        if (whenTrue.block() != null) visitBlock(whenTrue.block())
        else if (whenTrue.expression() != null) visitExpression(whenTrue.expression())

        if (!state.lastWhenEntry)
            state.code("goto ${labelMaker("whenEndLabel", state.whenCounter)}[adr]") // TODO lub nic dla ostatniego

        return state
    }

    override fun visitWhenCondition(ctx: KotlinParser.WhenConditionContext): WolinStateObject {
        when {
            ctx.expression() != null -> visitExpression(ctx.expression())
            ctx.rangeTest() != null -> błędzik(ctx, "nie wiem, jak się robi range test we when")
            ctx.typeTest() != null -> błędzik(ctx, "nie wiem, jak się robi type test")
        }

        return state
    }

    override fun visitIfExpression(ctx: KotlinParser.IfExpressionContext): WolinStateObject {
        val warunek = ctx.expression()
        val body = ctx.controlStructureBody()

        val condBoolRes = state.allocReg("condition expression bool result", Typ.bool)

        visitExpression(warunek)

        val valueForAssign = state.allocReg("for value when if assigned")

        if(state.assignStack.isNotEmpty())
            valueForAssign.type = state.assignStack.assignLeftSideVar.type


        val elseLabel = labelMaker("afterIfExpression", state.labelCounter)
        val endIfLabel = labelMaker("afterWholeIf", state.labelCounter)

        when {
            body.size == 1 -> {
                state.code("bne ${state.varToAsm(condBoolRes)} = #1[bool], $elseLabel<label_po_if>[adr]")
                state.rem(" body dla true")
                visitControlStructureBody(body[0])
                state.rem(" label po if")
                state.code("label $elseLabel")

            }
            body.size == 2 -> {
                //val elseLabel = labelMaker("elseExpression",state.labelCounter)

                state.code("bne ${state.varToAsm(condBoolRes)} = #1[bool], $elseLabel<label_DO_else>[adr]")
                state.rem(" body dla true")
                visitControlStructureBody(body[0])
                state.code("goto $endIfLabel[adr]")
                state.code("label $elseLabel")
                state.rem(" body dla false/else")
                visitControlStructureBody(body[1])
            }
            else -> {
                błędzik(ctx, "Unknown if body size - elseif?")
            }
        }

        state.code("label $endIfLabel")

        if(state.assignStack.isNotEmpty())
            checkTypeAndAddAssignment(ctx, state.assignStack.assignRightSideFinalVar, valueForAssign, "assign if expression result")

        state.freeReg("for value when if assigned")

        state.freeReg("condition expression bool result")

        state.labelCounter++

        return state
    }

    override fun visitTryExpression(ctx: KotlinParser.TryExpressionContext): WolinStateObject {
        val tryBlock = ctx.block()
        val catchNode = ctx.catchBlock(0)

        state.exceptionsUsed = true

        state.rem(" try")
        state.code("alloc SP<catch_addr>, #2 // catch block entry point")
        state.code("let SP(0)[uword] = ${labelMaker("catch_block_start", state.labelCounter)}[uword]")
        state.code("call __wolin_create_exception_entry[adr]")

        visitBlock(tryBlock)

        state.code("free SPE, #5 // remove exception table entry")
        state.code("goto ${labelMaker("after_catch_block_end", state.labelCounter)}[adr]")
        state.code("")
        state.rem(" catch")
        state.code("label ${labelMaker("catch_block_start", state.labelCounter)}")

        visitBlock(catchNode.block())

        state.code("")
        state.rem(" catch end")
        state.code("label ${labelMaker("after_catch_block_end", state.labelCounter)}")

        state.labelCounter++

        return state
    }

    fun checkTypeAndAddAssignment(ctx: ParseTree, doJakiej: Zmienna, co: Zmienna, comment: String) {
        if (state.pass == Pass.TRANSLATION) {
            val można =
                state.canBeAssigned(doJakiej.type, co.type) || doJakiej.type.isPointer || co.type.type == "uword"

            if (można) {
                state.code("let ${state.varToAsm(doJakiej)} = ${state.varToAsm(co)} // przez sprawdzacz typow - $comment")
            } else {
                błędzik(ctx, "Nie można przypisać ${co} do zmiennej typu ${doJakiej}")
            }
        }
    }

    override fun visitJumpExpression(ctx: KotlinParser.JumpExpressionContext): WolinStateObject {
        when {
            ctx.RETURN() != null -> {
                if (ctx.expression() != null) {
                    visitExpression(ctx.expression())
                }

                val zwrotka = state.callStack[0]

                checkTypeAndAddAssignment(ctx, zwrotka, state.currentReg, "jump expression")

                state.switchType(state.currentFunction!!.type, "return expression")
            }
            ctx.THROW() != null -> {
                if (ctx.expression() != null) {
                    visitExpression(ctx.expression())
                }

                state.exceptionsUsed = true

                state.code("throw SP(0)[uword] // nie martwimy sie o sotsy, bo te odtworzy obsluga wyjatku")
            }
            ctx.BREAK() != null -> {
                state.code("goto ${labelMaker("loopEnd", state.loopCounter)}[adr]")
                state.switchType(Typ.unit, "break expression")
            }
            ctx.CONTINUE() != null -> {
                state.code("goto ${labelMaker("loopStart", state.loopCounter)}[adr]")
                state.switchType(Typ.unit, "continue expression")
            }
            else -> {
                błędzik(ctx, "Unknown jump expression")
            }
        }

        return state
    }

    override fun visitSimpleIdentifier(ctx: KotlinParser.SimpleIdentifierContext): WolinStateObject {
        when {
            ctx.Identifier() != null -> {

                val zmienna = state.findVarInVariablaryWithDescoping(ctx.Identifier().text)


                if (zmienna.type.shortIndex && zmienna.type.elementOccupiesOneByte) {
                    state.currentShortArray = zmienna
                } else {
                    if (zmienna.allocation == AllocType.FIXED && zmienna.type.array) {
                        state.code(
                            "let ${state.currentRegToAsm()} = ${zmienna.location}[ptr] // simple id from fixed array var"
                        )
                    } else {
                        state.code(
                            "let ${state.currentRegToAsm()} = ${state.varToAsm(zmienna)} // simple id from var"
                        )
                    }
                }

                state.switchType(zmienna.type, "type from ${zmienna.name}")
            }
            else -> {
                błędzik(ctx, "Nie wiem jak obsłużyć ten simpleIdentifier")
            }
        }


        return state
    }

    override fun visitFunctionDeclaration(ctx: KotlinParser.FunctionDeclarationContext): WolinStateObject {
        val nowaFunkcja = Funkcja()

        val name = ctx.identifier()?.text ?: throw Exception("Brak nazwy funkcji")

        val lokacjaLiterał = ctx.locationReference()?.literalConstant()

        val lokacjaAdres = Zmienna("", allocation = AllocType.FIXED, stack = "dummy")

        state.switchType(Typ.unit, "function declaration")

        lokacjaLiterał?.let {
            parseLiteralConstant(lokacjaAdres, it)
        }

        nowaFunkcja.location = lokacjaAdres.intValue.toInt()
        nowaFunkcja.fullName = state.nameStitcher(name)

        state.currentFunction = nowaFunkcja

        val zwrotka = state.createAndRegisterVar("returnValue", ctx.type(0), null, true, "SPF")

        nowaFunkcja.type = zwrotka.type

        if (state.currentClass != null) {
            val thisArg = state.createAndRegisterVar(
                "this",
                AllocType.NORMAL,
                Typ.byName(state.currentClass!!.name, state),
                true,
                "SPF"
            )
            state.currentFunction?.arguments?.add(thisArg)
        }

        ctx.functionValueParameters()?.functionValueParameter()?.forEach {
            visitFunctionValueParameter(it)
        }

        val body = ctx.functionBody()

        if (body != null && nowaFunkcja.location != 0)
            throw Exception("Fixed address function ${nowaFunkcja.fullName} with a body!")

        state.code(
            "\n" + """// ****************************************
            |// funkcja: ${nowaFunkcja}
            |// ****************************************
        """.trimMargin()
        )

        state.code("label ${state.currentFunction!!.labelName}")

//        if(state.currentClass != null) {
//            //state.code("let __wolin_this_ptr[adr] = SPF(0)<this>[adr] // ustawienie this TODO - zapamiętać poprzedni this!!!!")
//        }

        state.fnDeclAllocStackAndRet(state.currentFunction!!)

        if (state.currentClass != null)
            state.code("setup HEAP = this")

        body?.let {
            visitFunctionBody(it)
        }

        state.code("")

        state.functiary.add(nowaFunkcja)

        state.currentFunction = null

        return state
    }


    override fun visitClassDeclaration(ctx: KotlinParser.ClassDeclarationContext): WolinStateObject {
        state.currentClass = Klasa(state.nameStitcher(ctx.simpleIdentifier().text))

        state.toClassary(state.currentClass!!)

        val konstruktor = Funkcja(
            state.currentClass!!.name,
            Typ.byName(state.currentClass!!.name, state)
        )


        ////////////////////////////////////////

        state.code(
            "\n" + """// ****************************************
            |// konstruktor: ${konstruktor}
            |// ****************************************
        """.trimMargin()
        )

        state.code("label ${konstruktor.labelName}")

        state.fnDeclAllocStackAndRet(konstruktor)

        state.code("")

        state.functiary.add(konstruktor)

        val typZwrotki = Typ(state.currentClass!!.name, false, true)

        val zwrotka = state.createAndRegisterVar("returnValue", AllocType.NORMAL, typZwrotki, true, "SPF")

        val funkcjaAlloc = state.findProc("allocMem").apply {
            if (state.pass == Pass.TRANSLATION) {
                val zKlasarium = state.findClass(state.currentClass!!.name)
                this.arguments[0].intValue = state.getStackSize(zKlasarium.heap).toLong()
                this.arguments[1].intValue = 1
            }
        }

        state.allocReg("for returning this", funkcjaAlloc.type)

        state.switchType(funkcjaAlloc.type, "function type 1")
        state.inferTopOperType()
        state.switchType(funkcjaAlloc.type, "function type 2")

        easeyCall(ctx, funkcjaAlloc, state.currentReg)

        state.rem(" tutaj kod na przepisanie z powyższego rejestru do zwrotki konstruktora")
        checkTypeAndAddAssignment(ctx, zwrotka, state.currentReg, "konstruktor")

        state.code("ret")

        ////////////////////////////////////////
        state.freeReg("for returning this")

        state.fnDeclFreeStackAndRet(konstruktor)

        val a = ctx.classBody()
        a.classMemberDeclaration()?.forEach {
            it.propertyDeclaration()?.let {
                visitPropertyDeclaration(it)
            }
        }

        val classUniqId = state.classCounter++

        val zmienna = Zmienna(
            "classUniqId",
            true,
            null,
            AllocType.NORMAL,
            stack = "HEAP",
            type = Typ.ubyte
        ).apply {
            intValue = classUniqId.toLong()
        }

        state.currentClass!!.heap.push(zmienna)

        // musi być rozdzielone, aby uwzględnić zmienną z id klasy
        a.classMemberDeclaration()?.forEach {
            it.functionDeclaration()?.let {
                visitFunctionDeclaration(it)
            }
        }


        state.currentClass = null

        return state
    }

    override fun visitFunctionValueParameter(ctx: KotlinParser.FunctionValueParameterContext): WolinStateObject {
        val param = ctx.parameter()

        val arg = state.createAndRegisterVar(param.simpleIdentifier().text, param.type(), null, true, "SPF")
        state.currentFunction?.arguments?.add(arg)


        return state
    }

    override fun visitTopLevelObject(ctx: KotlinParser.TopLevelObjectContext): WolinStateObject {
        ctx.propertyDeclaration()?.let {
            visitPropertyDeclaration(it)
        }

        ctx.classDeclaration()?.let {
            visitClassDeclaration(it)
        }

        ctx.functionDeclaration()?.let {
            visitFunctionDeclaration(it)
        }

        return state
    }


    override fun visitPropertyDeclaration(ctx: KotlinParser.PropertyDeclarationContext): WolinStateObject {
        val name = ctx.variableDeclaration().simpleIdentifier().text

        val stack = if (state.currentClass != null) "HEAP" else ""

        if (ctx.expression() != null) {

            state.allocReg("for var init expression")

            visitExpression(ctx.expression())
            state.inferTopOperType()

            val zmienna = if (ctx.variableDeclaration().type() != null) {
                state.createAndRegisterVar(name, ctx.variableDeclaration().type(), ctx, false, stack)
            } else
                state.createAndRegisterVar(name, AllocType.NORMAL, state.currentWolinType, false, stack)

            state.code("let ${state.varToAsm(zmienna)} = ${state.currentRegToAsm()} // podstawic wynik inicjalizacji expression do zmiennej $name")

            state.freeReg("for var init expression")
        } else {
            if (ctx.variableDeclaration().type() == null)
                błędzik(ctx, "Variable $name has no type!")

            val zmienna = state.createAndRegisterVar(name, ctx.variableDeclaration().type(), ctx, false, stack)

            //state.code("let ${state.varToAsm(zmienna)} = ${state.currentRegToAsm()} // podstawic wynik expression do zmiennej $name")
        }

        return state
    }

    override fun visitKotlinFile(ctx: KotlinParser.KotlinFileContext): WolinStateObject {
        // TODO: to powinno być includem dla c64
        if (state.exceptionsUsed) {
            state.code("setup SPE = 155[ubyte], 53247[uword] // exception stack pointer at 155 = 53247 (was: datasette something)")
            state.code("setup EXPTR = 178[ubyte] // ptr to Exception object when exception occurs (was: datasette buffer)")
            state.code("setup CATCH = 253[ubyte] // jmp adddress for catch")
        }

        if (state.spfUsed)
            state.code("setup SPF = 251[ubyte], 40959[uword] // call stack pointer at 251 = 40959")

        if (state.spUsed)
            state.code("setup SP = 143[ubyte] // register stack top = 142")

        if (state.mainFunction != null) {
            state.rem(" main function entry")
            state.code("goto ${state.mainFunction!!.labelName}[adr]")
        }

        //state.fileScopeSuffix = nazwaPliku
        ctx.preamble()?.let {
            visitPreamble(it)
        }

        ctx.topLevelObject()?.forEach {
            visitTopLevelObject(it)
        }

//        val test = dump(ctx,0)
//        print(test)


        if (state.exceptionsUsed) {
            state.code(
                "\n" + """// ****************************************
            |// Library modules
            |// ****************************************
        """.trimMargin()
            )

            state.code("")
            state.code("label __wolin_create_exception_entry")
            state.code("alloc SPE, #5")
            state.code("let SPE(0)[uword] = SP(0)[uword] // adres bloku catch, jeśli będzie exception")
            state.code("free SP, #2 //catch_addr")
            state.code("let SPE(2)[ubyte] = SPC[ubyte] // aktualny stos CPU, dwa odejmiemy od niego, jeśli będzie exception")
            state.code("let SPE(3)[ubyte] = SP[ubyte] // aktualny stos wolina")
            state.rem(" TODO - sprawdzić czy jego też trzeba zmniejszyć, bo catch jest wewnątrz allokacji dla statementu!")
            state.code("ret")

            state.code("")
            state.code("label __wolin_throw_exception")
            state.code("bne SPE = __wolin_spe_top, __wolin_process_exception // jesli mamy blok catch, to go obslugujemy")
            state.code("let __wolin_exception_ptr[ptr] = #0[uword]")
            state.code("crash")

            state.code("")
            state.code("label __wolin_process_exception")
            state.code("let __wolin_spe_zp_vector[uword] = SPE(0)[uword] // pobieramy adres bloku catch i ustawimy go jako wektor")
            state.code("add SPE(2)[ubyte] = SPE(2)[ubyte], #2[ubyte] // stos zapamiętaliśmy w podprogramie, musimy go zmniejszyć o adres powrotny")
            state.code("let SPC[ubyte] = SPE(2)[ubyte] // przywrócenie stosu CPU, takiego jak był w bloku try")
            state.code("let SP[ubyte] = SPE(3)[ubyte] // przywrócenie stosu wolina")
            state.code("free SPE, #5")
            state.code("goto __wolin_spe_zp_vector[ptr]")
        }

        return state
    }

    override fun visitLiteralConstant(ctx: KotlinParser.LiteralConstantContext): WolinStateObject {
        błędzik(ctx, "visitLiteralConstant should not be reached!")
        //state.rem("***** VISIT LITERAL CONSTANT")

        return state
    }


    override fun visitPreamble(ctx: KotlinParser.PreambleContext): WolinStateObject {
        state.basePackage = ctx.packageHeader()?.identifier()?.text ?: throw Exception("Illegal package name")
        return state
    }


    fun parseNumberGuessType(reg: Zmienna, text: String, radix: Int): Zmienna {
        val wynik = java.lang.Long.parseLong(text, radix)
        reg.intValue = wynik

        if (reg.type.isUnit) {
            when (wynik) {
                in 0..255 -> reg.type = Typ.byName("ubyte", state)
                in 0..65535 -> reg.type = Typ.byName("uword", state)
                in -127..128 -> reg.type = Typ.byName("byte", state)
                in -32768..32767 -> reg.type = Typ.byName("word", state)

                else -> throw Exception("Value out of range")
            }
        }

        //state.switchType(reg.type, "guess number type")

        return reg
    }

    fun parseLiteralConstant(reg: Zmienna, const: KotlinParser.LiteralConstantContext) {
        if (const.BinLiteral() != null) {

        } else if (const.BooleanLiteral() != null) {
            reg.type = Typ.byName("bool", state)
            if (const.text.toLowerCase() == "true") reg.intValue = 1 else reg.intValue = 0
        } else if (const.CharacterLiteral() != null) {
            reg.type = Typ.byName("ubyte", state)
            reg.intValue = const.text.first().toLong()
        } else if (const.HexLiteral() != null) {
            parseNumberGuessType(reg, const.text.drop(2), 16)
        } else if (const.IntegerLiteral() != null) {
            parseNumberGuessType(reg, const.text, 10)
        } else if (const.LongLiteral() != null) {
            parseNumberGuessType(reg, const.text, 10)
        } else if (const.NullLiteral() != null) {
            reg.intValue = 0L
            reg.type = Typ.byName("any?", state)
        } else if (const.RealLiteral() != null) {
            reg.type = Typ.byName("float", state)
            reg.floatValue = 3.14f
            state.floats.add(3.14f)
        } else if (const.stringLiteral() != null) {
            reg.type = Typ.byName("string", state)
            reg.stringValue = const.text
            state.strings.add(const.text)
        } else {
            błędzik(const, "Unknown literal")
        }

        state.switchType(reg.type, "parse literal constant")
        //state.switchType(reg.type, "parse literal constant")
    }

    fun błędzik(miejsce: ParseTree, teskt: String = "") {
        val interval = miejsce.sourceInterval
        val token = tokenStream.get(interval.a)
        val linia = token.line
        throw Exception("$teskt at $linia in:\"" + miejsce.text + "\"\n" + dump(miejsce, 0))
    }

    fun appendLambdas() {

        state.code(
            "\n\n" +
                    """// ****************************************
            |// LAMBDAS
            |// ****************************************
        """.trimMargin()
        )

        state.functiary.filter { it.lambdaBody != null }.forEach { nowaFunkcja ->

            state.switchType(Typ.unit, "function declaration")

            state.currentFunction = nowaFunkcja

            state.code(
                "\n" + """// ****************************************
            |// lambda: ${nowaFunkcja.fullName}
            |// ****************************************
        """.trimMargin()
            )

            state.code("label ${state.currentFunction!!.labelName}")

            state.fnDeclAllocStackAndRet(state.currentFunction!!)

            //state.allocReg("for lambda ${nowaFunkcja.name} return value",  nowaFunkcja.type?.type ?: "unit")

            state.allocReg("for lambda 'return value' ${state.currentFunction?.fullName}")

            state.currentFunction!!.lambdaBody?.statement()?.forEach {
                val zwrotka = state.callStack[0]
                statementProcessor(it, zwrotka)
            }

            state.freeReg("for lambda 'return value' ${state.currentFunction?.fullName}")

            state.switchType(state.currentFunction!!.type, "return expression")

            state.fnDeclFreeStackAndRet(state.currentFunction!!)

            state.code("ret")

            state.code("")

            state.currentFunction = null
        }
    }


    fun easeyCall(ctx: ParseTree, funkcjaAlloc: Funkcja, destReg: Zmienna) {
        state.rem(" otwarcie stosu na wywolanie ${funkcjaAlloc.fullName}")
        state.fnCallAllocRetAndArgs(funkcjaAlloc)

        state.rem(" tu podajemy argumenty dla ${funkcjaAlloc.fullName}")
        // wypełnić argumenty

        funkcjaAlloc.arguments.forEach {
            state.rem(" let ${it.name} = #${it.immediateValue}")

            val found = state.findStackVector(state.callStack, it.name)

            if (it.allocation == AllocType.FIXED) {
                state.code(
                    "let ${it.intValue} = #${it.immediateValue}"
                )
            } else {
                state.code(
                    "let SPF(${found.first})${found.second.typeForAsm} = #${it.immediateValue}${it.typeForAsm}"
                )
            }
        }

        state.rem(" po argumentach dla ${funkcjaAlloc.fullName}")
        state.code("call ${funkcjaAlloc.labelName}[adr]")

        state.fnCallReleaseArgs(funkcjaAlloc)

        checkTypeAndAddAssignment(ctx, destReg, state.callStack.peek(), "easey call")

        state.fnCallReleaseRet(funkcjaAlloc)

        state.code("free SPF <${funkcjaAlloc.type.type}>, #${funkcjaAlloc.type.sizeOnStack} // free return value of ${funkcjaAlloc.fullName} from call stack")
    }

    fun processTypeChangingOp(
        leftFoot: () -> Unit,
        rightFoot: () -> Unit,
        oper: (Zmienna, Zmienna, Zmienna) -> Typ,
        comment: String = "processTypeChangingOp"
    ) {
        val result = state.currentReg
        val leftReg = state.allocReg("LEFT $comment")
        leftFoot()
        state.inferTopOperType()
        val rightReg = state.allocReg("RIGHT $comment")
        rightFoot()
        state.inferTopOperType()
        val resultType = oper(result, leftReg, rightReg)
        state.freeReg("RIGHT $comment")
        state.freeReg("LEFT $comment")
        state.currentWolinType = resultType
        state.inferTopOperType()
    }


    fun processTypePreservingOp(
        leftFoot: () -> Unit,
        rightFoot: () -> Unit,
        oper: (Zmienna, Zmienna) -> Typ,
        comment: String = "processTypePreservingOp"
    ) {
        val leftReg = state.currentReg
        leftFoot()
        state.inferTopOperType()
        val rightReg = state.allocReg("RIGHT $comment")
        rightFoot()
        state.inferTopOperType()
        val resultType = oper(leftReg, rightReg)
        state.freeReg("RIGHT $comment")
        state.currentWolinType = resultType
        state.inferTopOperType()
    }


    fun simpleResultFunction(op: String, ret: Typ): (Zmienna, Zmienna) -> Typ {
        return { left, right ->

            state.code("$op ${state.varToAsm(left)} = ${state.varToAsm(left)}, ${state.varToAsm(right)}")

            ret
        }
    }

    fun simpleResultFunction(op: String): (Zmienna, Zmienna) -> Typ {
        return { left, right ->

            state.code("$op ${state.varToAsm(left)} = ${state.varToAsm(left)}, ${state.varToAsm(right)}")

            state.currentWolinType
        }
    }


}


fun labelMaker(name: String, count: Int): String = "__wolin_lab_${name}_$count"
