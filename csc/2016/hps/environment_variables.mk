export RM=rm
export MAKE=make
export C_COMPILER=clang
export CPP_COMPILER=clang++

ifeq (${PROFILE},perf) 
export COMPILER_FLAGS=-Ofast -fslp-vectorize-aggressive
export BUILDDIR=$(dir $(lastword $(MAKEFILE_LIST)))/perf
else
export COMPILER_FLAGS=-Werror -pedantic-errors -Wextra-tokens -Wambiguous-member-template -Wbind-to-temporary-copy
export BUILDDIR=$(dir $(lastword $(MAKEFILE_LIST)))/build
endif
