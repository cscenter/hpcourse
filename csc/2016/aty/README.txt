This folder contains two different versions of the server.

The first one (HPS folder) does not use any built-in synchronization mechanisms except for atomic variables. A spin mutex is implemented based on atomics and used for synchronization. 

I did so because I thought that not using any existing primitives is a restriction for this task but later I realised that it is hardly possible to be true. This is why I decided to write the second version using built-in synchronization and the wait-notify mechanism. I hope that this misunderstading will not become a big problem.

The second version is contained in the HPS2 folder. It implements a simple thread pool with configurable, but not changeable number of threads. Wait-notify mechanism is used for implementation of the thread pool and for waiting on tasks completion (to start dependent tasks and to send subscription responses).
