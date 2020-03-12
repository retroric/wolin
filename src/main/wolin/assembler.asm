setup HEADER
setup SPF = 251[ubyte], 40959[uword] // call stack pointer at 251 = 40959
setup SP = 114[ubyte] // (było 143) register stack top = 142
setup HEAP = 176[ubyte]
// inicjalizacja zmiennej pl.qus.wolin.i
alloc SP<__wolin_reg0>, #1 // for var pl.qus.wolin.i init expression
// switchType to:ubyte by parse literal constant
let SP(0)<__wolin_reg0>[ubyte] = #0[ubyte] // atomic ex
// top type already set: __wolin_reg0: ubyte = 0 (for var pl.qus.wolin.i init expression) location = null, null
let __wolin_pl_qus_wolin_i<pl.qus.wolin.i>[uword] = SP(0)<__wolin_reg0>[ubyte] // podstawic wynik inicjalizacji expression do zmiennej pl.qus.wolin.i
free SP<__wolin_reg0>, #1 // for var pl.qus.wolin.i init expression
// inicjalizacja zmiennej pl.qus.wolin.chr
alloc SP<__wolin_reg1>, #1 // for var pl.qus.wolin.chr init expression
// switchType to:ubyte by parse literal constant
let SP(0)<__wolin_reg1>[ubyte] = #0[ubyte] // atomic ex
// top type already set: __wolin_reg1: ubyte = 0 (for var pl.qus.wolin.chr init expression) location = null, null
let __wolin_pl_qus_wolin_chr<pl.qus.wolin.chr>[ubyte] = SP(0)<__wolin_reg1>[ubyte] // podstawic wynik inicjalizacji expression do zmiennej pl.qus.wolin.chr
free SP<__wolin_reg1>, #1 // for var pl.qus.wolin.chr init expression
//  main function entry
//  otwarcie stosu na wywolanie pl.qus.wolin.main
alloc SPF, #0
//  tu podajemy argumenty dla pl.qus.wolin.main
//  po argumentach dla pl.qus.wolin.main
call __wolin_pl_qus_wolin_main[uword]
endfunction
// switchType to:uword by parse literal constant
// switchType to:unit by function declaration

// ****************************************
// funkcja: fun pl.qus.wolin.test(pl.qus.wolin.test.wart: ubyte = 0 () location = null, null):unit
// ****************************************
function __wolin_pl_qus_wolin_test
alloc SP<__wolin_reg3>, #1 // for blockLevel expression
label __wolin_lab_loop_start_1
// FORCE TOP: __wolin_reg3: bool = 0 (for blockLevel expression) location = null, null -> bool
// 
// == ASSIGNMENT PUSH =======================================
// 
// == ASSIGNMENT LEFT =======================================
alloc SP<__wolin_reg6>, #2 // ASSIGNMENT target
// (do assignLeftSideVar przypisano __wolin_reg6: ubyte* = 0 (ASSIGNMENT target) location = null, null)
alloc SP<__wolin_reg7>, #2 // arr_deref
//  LEWA strona array access, czyli co to za zmienna
let SP(0)<__wolin_reg7>[ubyte*] = #1024[uword] // simple id - fixed array var
// switchType to:ubyte* by type from pl.qus.wolin.screen
//  PRAWA strona array access, czyli indeks w nawiasach
alloc SP<__wolin_reg8>, #2 // For calculating index
let SP(0)<__wolin_reg8>[uword*] = *__wolin_pl_qus_wolin_i<pl.qus.wolin.i>[uword] // przez sprawdzacz typow - simple id from var
// switchType to:uword by type from pl.qus.wolin.i
// FORCE TOP: __wolin_reg8: uword = 0 (For calculating index) location = null, null -> uword
add SP(2)<__wolin_reg7>[ubyte*] = SP(2)<__wolin_reg7>[ubyte*], &SP(0)<__wolin_reg8>[uword] // long index, single byte per element array (tutaj)
free SP<__wolin_reg8>, #2 // For calculating index
// **ARRAY Changing current type to prevReg type __wolin_reg7: ubyte* = 0 (arr_deref) location = null, null
//  after index
// dereference value at topRegister
//  kod obsługi tablicy
//  non-fast array, changing top reg to ptr
let SP(2)<__wolin_reg6>[ubyte*] = SP(0)<__wolin_reg7>[ubyte*] // przez sprawdzacz typow - non-fast array
free SP<__wolin_reg7>, #2 // arr_deref
// top type already set: __wolin_reg6: ubyte* = 0 (ASSIGNMENT target) location = null, null
// == ASSIGNMENT RIGHT =======================================
alloc SP<__wolin_reg9>, #2 // ASSIGNMENT value
// (do assignRightSideFinalVar 1 przypisano __wolin_reg9: ubyte* = 0 (ASSIGNMENT value) location = null, null)
let SP(0)<__wolin_reg9>[ubyte*] = *__wolin_pl_qus_wolin_chr<pl.qus.wolin.chr>[ubyte] // przez sprawdzacz typow - simple id from var
// switchType to:ubyte by type from pl.qus.wolin.chr
let &SP(2)<__wolin_reg6>[ubyte*] = &SP(0)<__wolin_reg9>[ubyte*] // przez sprawdzacz typow - process assignment
free SP<__wolin_reg9>, #2 // ASSIGNMENT value, type = ubyte*
free SP<__wolin_reg6>, #2 // ASSIGNMENT target
// == ASSIGNMENT END =======================================
// == ASSIGNMENT POP =======================================
// 
// switchType to:unit by assignment
// top type already set: __wolin_reg5: unit = 65535 (for blockLevel expression) location = null, null
alloc SP<__wolin_reg10>, #2 // for blockLevel expression
let SP(0)<__wolin_reg10>[uword*] = *__wolin_pl_qus_wolin_i<pl.qus.wolin.i>[uword] // przez sprawdzacz typow - operator ++
add __wolin_pl_qus_wolin_i<pl.qus.wolin.i>[uword] = __wolin_pl_qus_wolin_i<pl.qus.wolin.i>[uword], #1[uword] // simple id
// switchType to:uword by ++ operator
// top type already set: __wolin_reg10: uword* = 0 (for blockLevel expression) location = null, null
free SP<__wolin_reg10>, #2 // for blockLevel expression
alloc SP<__wolin_reg11>, #2 // for blockLevel expression
let SP(0)<__wolin_reg11>[ubyte*] = *__wolin_pl_qus_wolin_chr<pl.qus.wolin.chr>[ubyte] // przez sprawdzacz typow - operator ++
add __wolin_pl_qus_wolin_chr<pl.qus.wolin.chr>[ubyte] = __wolin_pl_qus_wolin_chr<pl.qus.wolin.chr>[ubyte], #1[ubyte] // simple id
// switchType to:ubyte by ++ operator
// top type already set: __wolin_reg11: ubyte* = 0 (for blockLevel expression) location = null, null
free SP<__wolin_reg11>, #2 // for blockLevel expression
alloc SP<__wolin_reg12>, #2 // LEFT for <
let SP(0)<__wolin_reg12>[uword*] = *__wolin_pl_qus_wolin_i<pl.qus.wolin.i>[uword] // przez sprawdzacz typow - simple id from var
// switchType to:uword by type from pl.qus.wolin.i
// top type already set: __wolin_reg12: uword* = 0 (LEFT for <) location = null, null
alloc SP<__wolin_reg13>, #2 // RIGHT for <
// switchType to:uword by parse literal constant
let SP(0)<__wolin_reg13>[uword] = #1000[uword] // atomic ex
// top type already set: __wolin_reg13: uword = 0 (RIGHT for <) location = null, null
evalless &SP(4)<__wolin_reg3>[bool] = &SP(2)<__wolin_reg12>[uword*], &SP(0)<__wolin_reg13>[uword]
free SP<__wolin_reg13>, #2 // RIGHT for <
free SP<__wolin_reg12>, #2 // LEFT for <
// top type already set: __wolin_reg3: bool = 0 (for blockLevel expression) location = null, null
beq SP(0)<__wolin_reg3>[bool] = #1[bool], __wolin_lab_loop_start_1<label_po_if>[uword]
label __wolin_lab_loop_end_1
// top type already set: __wolin_reg3: bool = 0 (for blockLevel expression) location = null, null
free SP<__wolin_reg3>, #1 // for blockLevel expression
free SPF<pl.qus.wolin.test.__fnargs>, #1 // free fn arguments and locals for pl.qus.wolin.test
// freeing call stack
// return from function body
endfunction

// switchType to:unit by function declaration

// ****************************************
// funkcja: fun pl.qus.wolin.main():unit
// ****************************************
function __wolin_pl_qus_wolin_main
// switchType to:unit by function return type 1
// 
// == FN_CALL: pl.qus.wolin.test ========
alloc SPF, #1
// == FN_CALL: ARG #0 (5) pl.qus.wolin.test
alloc SP<__wolin_reg16>, #1 // for call argument 0
// Prze visit vALUE
//  obliczenia dla parametru 5
// switchType to:ubyte by parse literal constant
let SP(0)<__wolin_reg16>[ubyte] = #5[ubyte] // atomic ex
// po visit value
let SPF(0)[ubyte] = SP(0)<__wolin_reg16>[ubyte]
free SP<__wolin_reg16>, #1 // for call argument 0, type = ubyte
// switchType to:unit by function return type 2
// switchType to:unit by function call
call __wolin_pl_qus_wolin_test[uword]

// == FN_CALL END: pl.qus.wolin.test ========
// 
// top type already set: __wolin_reg15: unit = 65535 (for blockLevel expression) location = null, null
// freeing call stack
// return from function body
endfunction



// ****************************************
// LAMBDAS
// ****************************************


// ****************************************
// STATIC SPACE
// ****************************************
label __wolin_pl_qus_wolin_i
alloc 0[uword]  // pl.qus.wolin.i
label __wolin_pl_qus_wolin_chr
alloc 0[ubyte]  // pl.qus.wolin.chr
