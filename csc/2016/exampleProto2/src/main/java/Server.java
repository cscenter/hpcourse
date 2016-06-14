import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

//логика работы такая, что я создаю новый thread для каждого ServerRequest,
// который непосредственно занимается только им
class Server extends Thread {
    ArgsOfTask args;//аргументы на задачу, имеется ввиду a,m ...
    OneSession oneSession;//вся информация о результатах одной сессии с данным сокетом
    OneSessionLogicInformation logicInformation;//информация для обработки задачи, monitor...
    static HashMap<Long, OneSession> mapTasks;//мапа задач, в ней содержатся все результаты и прочая информация
    static HashMap<Long, Object> mapMonitor;//мапа мониторов на них

    public static void main(String args[]) throws IOException, InterruptedException {
        mapTasks = new HashMap(200);
        mapMonitor = new HashMap(200);
        int i = 0;
        ServerSocket server = new ServerSocket(3129, 0,
                InetAddress.getByName("localhost"));
        System.out.println("server is started");

        while (true) {
            new Server(i, server.accept());//как услышали новую задачу, создаём поток с номером i
            i++;
        }
    }

    //инцилизация
    public Server(int numOfThread, Socket socket) throws IOException, InterruptedException {
        logicInformation = new OneSessionLogicInformation();
        args = new ArgsOfTask();
        this.oneSession = new OneSession();
        this.logicInformation.setNumOfThread(numOfThread);
        this.logicInformation.setSocket(socket);
        this.logicInformation.setMonitor(new Object());
        setDaemon(true);
        setPriority(NORM_PRIORITY);
        start();
    }

    public void run() {
        try {

            //читаем
            readDataAndCompute();
            OutputStream os = logicInformation.getSocket().getOutputStream();
            //задача, которую решаем имеет плохие зависимые задачи(их нет)
            if (!oneSession.statusTask) {
                System.out.println("It is broken Task");
            } else {
                //если вычисления все сделаны, то есть task уже была до этого решенной
                if (logicInformation.getComputeIsEnd()) {
                    args.setA(mapTasks.get(logicInformation.getKeyOfTask()).val);
                    System.out.println("Task " + logicInformation.getKeyOfTask() +
                            " return " + args.getA() + " immediately!");
                } else {
                    //иначе решаем
                    computeTask();
                    //меняем значения как для решённой таски
                    //так как это происходит после считывания, попасть на ошибку, что задача не может быть решена
                    //из-за зависимых аргументов, мы не можем, так как иначе бы вышли в if на уровне раньше
                    oneSession.MySessionChange(args.getA(), 2, oneSession.client_id, oneSession.task, oneSession);
                    logicInformation.setComputeIsEnd(true);
                }
            }
            //создаём ответ
            oneSession.createResponse(mapTasks).writeDelimitedTo(os);
            //это просто, чтобы при тесте server не молчал
            System.out.println("I have sent result " + args.getA() +
                    " to " + logicInformation.getKeyOfTask() + "\n");
            //как задача решена, мы говорим всем проснуться
            //не может быть deadlock, так как если, кто в wait мы его уведомим
            //если кто попал на sychronized, то он это сделал либо порешённо задачи и тогда, он не wait
            //либо не по решённой, но ресурс захвачен и задача не в конечной стадии, значит успеет добежать до
            //wait, до notifyAll, смотрите метод dependentTaskReadAndWait
            synchronized (logicInformation.getMonitor()) {
                logicInformation.getMonitor().notifyAll();
            }
        } catch (Exception e) {
            System.out.println("init error: " + e);
            e.printStackTrace();
        } finally {
            //закрываем socket
            try {
                logicInformation.getSocket().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void readDataAndCompute() throws IOException, InterruptedException {
        try {
            Protocol.WrapperMessage message = Protocol.WrapperMessage.parseDelimitedFrom(logicInformation.
                    getSocket().getInputStream());
            if (message.hasRequest()) {
                Protocol.ServerRequest request = message.getRequest();
                //собственно переходим на вложенный метод, который обрабатывает request
                serverRequest(request);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("I was given not wrapper message.");
        }
    }

    void serverRequest(Protocol.ServerRequest request) throws InterruptedException {
        logicInformation.setKeyOfTask(request.getRequestId());
        oneSession.setClient_id(request.getClientId());
        oneSession.setRequest_id((int) logicInformation.getKeyOfTask());
        oneSession.setTask(null);
        //логика, если хотят лист
        if (request.hasList()) {
            oneSession.hasList = true;
        }
        //логика, если подписываются
        if (request.hasSubscribe()) {
            oneSession.hasSubscribe = true;
            long keySubscribe = request.getSubscribe().getTaskId();
            oneSession.subscribeTaskId = keySubscribe;
            if (mapTasks.containsKey(keySubscribe) && mapTasks.get(keySubscribe).stage == 2) {
                oneSession.statusSubscribeTask = true;
                oneSession.valSubscribeTask = mapTasks.get(keySubscribe).val;
            } else {
                if (!mapTasks.containsKey(keySubscribe)) {
                    oneSession.statusSubscribeTask = false;
                } else {
                    synchronized (mapMonitor.get(keySubscribe)) {
                        if (mapTasks.get(keySubscribe).stage != 2) {
                            mapMonitor.get(keySubscribe).wait();
                        }
                        oneSession.valSubscribeTask = mapTasks.get(keySubscribe).val;
                        System.out.println("subscribe task is already here and solved");
                    }
                    oneSession.statusSubscribeTask = true;
                }
            }
        }
        //логика, если присылают свою таску
        if (request.hasSubmit()) {
            oneSession.hasSubmit = true;
            if (mapTasks.containsKey(logicInformation.getKeyOfTask()) &&
                    mapTasks.get(logicInformation.getKeyOfTask()).stage == 2) {
                logicInformation.setComputeIsEnd(true);
            } else {
                Protocol.Task task = request.getSubmit().getTask();
                oneSession.task = task;
                //быстро отвечаем на независимые от других задач запросы, посылая уведомление, что взяли в обработку
                //если будет 0, то посылаем сообщение с флагом об отказе
                quickAnswer.quickAnswer(oneSession.getTask(), oneSession.getRequest_id(), logicInformation.getSocket());
                //читаем аргументы, если зависимые, то ждём решения
                args.setA(dependentTaskReadAndWait(task.getA()));
                args.setB(dependentTaskReadAndWait(task.getB()));
                args.setP(dependentTaskReadAndWait(task.getP()));
                args.setM(dependentTaskReadAndWait(task.getM()));
                args.setN(task.getN());
                if(args.getN()==0) {
                    //игнорируем сообщение
                    //ввиду того, что хочется записывать результаты хороших задач
                    //под тем же id можно будет прислать эту task с другим n
                    logicInformation.setComputeIsEnd(true);
                    System.out.println("I have been given zero!");
                    throw (new ArithmeticException());
                }
                //тут предполагается, что между запросом и таской, есть биекция
                //иначе бы добавили бы ещё один if
                if (!mapTasks.containsKey(logicInformation.getKeyOfTask())) {
                    if (!logicInformation.getSkipCompute()) {
                        synchronized (mapMonitor) {
                            synchronized (mapTasks) {
                                //присваиваем новое значение
                                //1-значит ещё не решана
                                OneSession.MySessionChange(0, 1, oneSession.client_id, task, oneSession);
                                //кладём в мапу
                                mapTasks.put(logicInformation.getKeyOfTask(), oneSession);
                                //наш monitor тоже кладём в map
                                mapMonitor.put(logicInformation.getKeyOfTask(), logicInformation.getMonitor());
                            }
                        }
                        oneSession.setVal(mapTasks.get(logicInformation.getKeyOfTask()).val);
                        System.out.println("Put in map of tasks " + logicInformation.getKeyOfTask());
                    }
                } else {
                    //в случае, если задача уже решена до этого
                    //ставим флаг, что вычислять ничего не надо
                    System.out.println("it already is in map of tasks ");
                    logicInformation.setComputeIsEnd(true);
                }
            }
        }
    }

    //вычисление задачи
    private long computeTask() throws InterruptedException {
        //если зависимой задачи нет, то statusTask это покажет, тогда нам нечего считать
        //выходим
        if (!oneSession.statusTask) {
            System.out.println("Skip compute");
            return 0;
        }
        System.out.println("Compute task " + logicInformation.getKeyOfTask() + "!");
        long a = args.getA();
        long b = args.getB();
        long p = args.getP();
        long m = args.getM();
        long n = args.getN();
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
            Thread.currentThread().sleep(10);
        }
        //после счёта присваиваем новые значения
        //задержка для тестов
        args.setB(b);
        args.setA(a);
        args.setN(n);
        return a;
    }

    //решаем зависимые задачи
    long dependentTaskReadAndWait(Protocol.Task.Param param) throws InterruptedException {
        if (!oneSession.statusTask) return -1;
        if (param.hasValue()) {
            return param.getValue();
        } else {
            if (!mapTasks.containsKey(new Long(param.getDependentTaskId()))) {
                oneSession.statusTask = false;
                System.out.println("такой зависимой задачи нет " + param.getDependentTaskId() + " для нашей задачи " +
                        oneSession.request_id);
                logicInformation.setSkipCompute(true);
                return 0;
            } else {
                long key = param.getDependentTaskId();

                synchronized (mapMonitor.get(key)) {
                    if (mapTasks.get(key).stage != 2) {
                        mapMonitor.get(key).wait();
                    }
                    System.out.println("зависимая задача " + param.getDependentTaskId() +
                            " для задачи " + logicInformation.getKeyOfTask() + " решена");
                    return mapTasks.get(key).val;
                }
            }
        }
    }
}



