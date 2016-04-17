include environment_variables.mk

all:
	${MAKE} -C apps/simple_task
	${MAKE} -C core/server 
	${MAKE} -C core/client 

clean:
	${RM} -rf ${BUILDDIR}
