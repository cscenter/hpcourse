include ../../environment_variables.mk 

all: $(BUILDDIR) $(BUILDDIR)/libsimple.so

clean:
	rm -r ${BUILDDIR}/libsimple.so ${BUILDDIR}/simple.o

$(BUILDDIR)/libsimple.so: ${BUILDDIR}/simple.o
	${C_COMPILER} -shared -o $@ $< 
	
${BUILDDIR}/simple.o: simple.c
	${C_COMPILER} ${COMPILER_FLAGS} -c $< -o $@ 

${BUILDDIR}:
	mkdir -p ${BUILDDIR}
