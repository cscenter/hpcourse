import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

//������ ������ �����, ��� � ������ ����� thread ��� ������� ServerRequest,
// ������� ��������������� ���������� ������ ��
class Server extends Thread {
    ArgsOfTask args;//��������� �� ������, ������� ����� a,m ...
    OneSession oneSession;//��� ���������� � ����������� ����� ������ � ������ �������
    OneSessionLogicInformation logicInformation;//���������� ��� ��������� ������, monitor...
    static HashMap<Long, OneSession> mapTasks;//���� �����, � ��� ���������� ��� ���������� � ������ ����������
    static HashMap<Long, Object> mapMonitor;//���� ��������� �� ���

    public static void main(String args[]) throws IOException, InterruptedException {
        mapTasks = new HashMap(200);
        mapMonitor = new HashMap(200);
        int i = 0;
        ServerSocket server = new ServerSocket(3129, 0,
                InetAddress.getByName("localhost"));
        System.out.println("server is started");

        while (true) {
            new Server(i, server.accept());//��� �������� ����� ������, ������ ����� � ������� i
            i++;
        }
    }

    //�����������
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

            //������
            readDataAndCompute();
            OutputStream os = logicInformation.getSocket().getOutputStream();
            //������, ������� ������ ����� ������ ��������� ������(�� ���)
            if (!oneSession.statusTask) {
                System.out.println("It is broken Task");
            } else {
                //���� ���������� ��� �������, �� ���� task ��� ���� �� ����� ��������
                if (logicInformation.getComputeIsEnd()) {
                    args.setA(mapTasks.get(logicInformation.getKeyOfTask()).val);
                    System.out.println("Task " + logicInformation.getKeyOfTask() +
                            " return " + args.getA() + " immediately!");
                } else {
                    //����� ������
                    computeTask();
                    //������ �������� ��� ��� �������� �����
                    //��� ��� ��� ���������� ����� ����������, ������� �� ������, ��� ������ �� ����� ���� ������
                    //��-�� ��������� ����������, �� �� �����, ��� ��� ����� �� ����� � if �� ������ ������
                    oneSession.MySessionChange(args.getA(), 2, oneSession.client_id, oneSession.task, oneSession);
                    logicInformation.setComputeIsEnd(true);
                }
            }
            //������ �����
            oneSession.createResponse(mapTasks).writeDelimitedTo(os);
            //��� ������, ����� ��� ����� server �� ������
            System.out.println("I have sent result " + args.getA() +
                    " to " + logicInformation.getKeyOfTask() + "\n");
            //��� ������ ������, �� ������� ���� ����������
            //�� ����� ���� deadlock, ��� ��� ����, ��� � wait �� ��� ��������
            //���� ��� ����� �� sychronized, �� �� ��� ������ ���� ��������� ������ � �����, �� �� wait
            //���� �� �� ��������, �� ������ �������� � ������ �� � �������� ������, ������ ������ �������� ��
            //wait, �� notifyAll, �������� ����� dependentTaskReadAndWait
            synchronized (logicInformation.getMonitor()) {
                logicInformation.getMonitor().notifyAll();
            }
        } catch (Exception e) {
            System.out.println("init error: " + e);
            e.printStackTrace();
        } finally {
            //��������� socket
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
                //���������� ��������� �� ��������� �����, ������� ������������ request
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
        //������, ���� ����� ����
        if (request.hasList()) {
            oneSession.hasList = true;
        }
        //������, ���� �������������
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
        //������, ���� ��������� ���� �����
        if (request.hasSubmit()) {
            oneSession.hasSubmit = true;
            if (mapTasks.containsKey(logicInformation.getKeyOfTask()) &&
                    mapTasks.get(logicInformation.getKeyOfTask()).stage == 2) {
                logicInformation.setComputeIsEnd(true);
            } else {
                Protocol.Task task = request.getSubmit().getTask();
                oneSession.task = task;
                //������ �������� �� ����������� �� ������ ����� �������, ������� �����������, ��� ����� � ���������
                //���� ����� 0, �� �������� ��������� � ������ �� ������
                quickAnswer.quickAnswer(oneSession.getTask(), oneSession.getRequest_id(), logicInformation.getSocket());
                //������ ���������, ���� ���������, �� ��� �������
                args.setA(dependentTaskReadAndWait(task.getA()));
                args.setB(dependentTaskReadAndWait(task.getB()));
                args.setP(dependentTaskReadAndWait(task.getP()));
                args.setM(dependentTaskReadAndWait(task.getM()));
                args.setN(task.getN());
                if(args.getN()==0) {
                    //���������� ���������
                    //����� ����, ��� ������� ���������� ���������� ������� �����
                    //��� ��� �� id ����� ����� �������� ��� task � ������ n
                    logicInformation.setComputeIsEnd(true);
                    System.out.println("I have been given zero!");
                    throw (new ArithmeticException());
                }
                //��� ��������������, ��� ����� �������� � ������, ���� �������
                //����� �� �������� �� ��� ���� if
                if (!mapTasks.containsKey(logicInformation.getKeyOfTask())) {
                    if (!logicInformation.getSkipCompute()) {
                        synchronized (mapMonitor) {
                            synchronized (mapTasks) {
                                //����������� ����� ��������
                                //1-������ ��� �� ������
                                OneSession.MySessionChange(0, 1, oneSession.client_id, task, oneSession);
                                //����� � ����
                                mapTasks.put(logicInformation.getKeyOfTask(), oneSession);
                                //��� monitor ���� ����� � map
                                mapMonitor.put(logicInformation.getKeyOfTask(), logicInformation.getMonitor());
                            }
                        }
                        oneSession.setVal(mapTasks.get(logicInformation.getKeyOfTask()).val);
                        System.out.println("Put in map of tasks " + logicInformation.getKeyOfTask());
                    }
                } else {
                    //� ������, ���� ������ ��� ������ �� �����
                    //������ ����, ��� ��������� ������ �� ����
                    System.out.println("it already is in map of tasks ");
                    logicInformation.setComputeIsEnd(true);
                }
            }
        }
    }

    //���������� ������
    private long computeTask() throws InterruptedException {
        //���� ��������� ������ ���, �� statusTask ��� �������, ����� ��� ������ �������
        //�������
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
        //����� ����� ����������� ����� ��������
        //�������� ��� ������
        args.setB(b);
        args.setA(a);
        args.setN(n);
        return a;
    }

    //������ ��������� ������
    long dependentTaskReadAndWait(Protocol.Task.Param param) throws InterruptedException {
        if (!oneSession.statusTask) return -1;
        if (param.hasValue()) {
            return param.getValue();
        } else {
            if (!mapTasks.containsKey(new Long(param.getDependentTaskId()))) {
                oneSession.statusTask = false;
                System.out.println("����� ��������� ������ ��� " + param.getDependentTaskId() + " ��� ����� ������ " +
                        oneSession.request_id);
                logicInformation.setSkipCompute(true);
                return 0;
            } else {
                long key = param.getDependentTaskId();

                synchronized (mapMonitor.get(key)) {
                    if (mapTasks.get(key).stage != 2) {
                        mapMonitor.get(key).wait();
                    }
                    System.out.println("��������� ������ " + param.getDependentTaskId() +
                            " ��� ������ " + logicInformation.getKeyOfTask() + " ������");
                    return mapTasks.get(key).val;
                }
            }
        }
    }
}



