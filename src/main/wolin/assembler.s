; setupSPF=251[ubyte],40959[uword]


; prepare function stack
__wolin_spf = 251 ; function stack ptr
__wolin_spf_top = 40959 ; function stack top
    lda #<__wolin_spf_top ; set function stack top
    sta __wolin_spf
    lda #>__wolin_spf_top
    sta __wolin_spf+1

; setupSP=143[ubyte]


; prepare program stack
__wolin_sp_top = 143 ; program stack top
    ldx #__wolin_sp_top ; set program stack top

; goto__wolin_pl_qus_wolin_test_main[adr]

    jmp __wolin_pl_qus_wolin_test_main

; label__wolin_pl_qus_wolin_test_main

__wolin_pl_qus_wolin_test_main:

; allocSP<__wolin_reg1>,#2


    dex
    dex

; allocSP<__wolin_reg2>,#2


    dex
    dex

; letSP(0)<__wolin_reg2>[ptr]=__wolin_pl_qus_wolin_test_twoBytesLongArray<pl.qus.wolin.test.twoBytesLongArray>[ptr]


    lda #<__wolin_pl_qus_wolin_test_twoBytesLongArray
    sta 0,x
    lda #>__wolin_pl_qus_wolin_test_twoBytesLongArray
    sta 0+1,x

; allocSP<__wolin_reg3>,#2


    dex
    dex

; letSP(0)<__wolin_reg3>[uword]=#400[uword]


    lda #<400
    sta 0,x
    lda #>400
    sta 0+1,x

; mulSP(0)<__wolin_reg3>[uword]=SP(0)<__wolin_reg3>[uword],#2

   asl 0,x

; addSP(2)<__wolin_reg2>[ptr]=SP(2)<__wolin_reg2>[ptr],SP(0)<__wolin_reg3>[uword]


    clc
    lda 2,x
    adc 0,x
    sta 2,x
    lda 2+1,x
    adc 0+1,x
    sta 2+1,x

; freeSP<__wolin_reg3>,#2


    inx
    inx

; letSP(2)<__wolin_reg1>[ptr]=SP(0)<__wolin_reg2>[ptr]


    lda 0,x
    sta 2,x
    lda 0+1,x
    sta 2+1,x


; freeSP<__wolin_reg2>,#2


    inx
    inx

; allocSP<__wolin_reg4>,#2


    dex
    dex

; letSP(0)<__wolin_reg4>[uword]=#100[ubyte]


    lda #100
    sta 0,x
    lda #0
    sta 0+1,x

; freeSP<__wolin_reg4>,#2


    inx
    inx

; freeSP<__wolin_reg1>,#2


    inx
    inx

; ret

    rts

; label__wolin_indirect_jsr

__wolin_indirect_jsr:

; goto65535[adr]

    jmp 65535

; label__wolin_pl_qus_wolin_test_twoBytesLongArray

__wolin_pl_qus_wolin_test_twoBytesLongArray:

; alloc0[ptr]

    .word 0

; label__wolin_pl_qus_wolin_test_c

__wolin_pl_qus_wolin_test_c:

; alloc0[uword]

    .word 0

; label__wolin_pl_qus_wolin_test_b

__wolin_pl_qus_wolin_test_b:

; alloc0[ubyte]

    .byte 0

; label__wolin_pl_qus_wolin_test_d

__wolin_pl_qus_wolin_test_d:

; alloc0[uword]

    .word 0

; label__wolin_pl_qus_wolin_test_oneByteLongArray

__wolin_pl_qus_wolin_test_oneByteLongArray:

; alloc0[ptr]

    .word 0

; label__wolin_pl_qus_wolin_test_fastArray

__wolin_pl_qus_wolin_test_fastArray:

; alloc0[ptr]

    .word 0

