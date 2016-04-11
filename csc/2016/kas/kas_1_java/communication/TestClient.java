package communication;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import communication.Protocol.ListTasks;
import communication.Protocol.ServerRequest;
import communication.Protocol.ServerResponse;
import communication.Protocol.Status;
import communication.Protocol.SubmitTask;
import communication.Protocol.SubmitTaskResponse;
import communication.Protocol.Subscribe;
import communication.Protocol.SubscribeResponse;
import communication.Protocol.Task;
import communication.Protocol.Task.Param;

public class TestClient {
	private static final String DEFAULT_HOST = "localhost";
	private static final int DEFAULT_PORT = 1036;
	static long curId = 0;
	static String clientId = "test";
	static Socket socket;
	static Random random = new Random();
	static final long MAXN = 5000000000L;
	public static void main(String[] args) throws IOException, InterruptedException {
		String host = DEFAULT_HOST;
		int port = DEFAULT_PORT;
		if (args.length >= 1) {
			String[] ss = args[0].split(":");
			host = ss[0];
			if (ss.length > 1) {
				port = Integer.parseInt(ss[1]);
			}
		}
		socket = new Socket(host, port);
		testFullRandom();
		socket.close();
	}
	static ArrayList<ServerRequest> requests = new ArrayList<>();
	static ArrayList<ServerResponse> responses = new ArrayList<>();
	static HashMap<Integer, Long> resultsByTask = new HashMap<>();
	static HashMap<Long, ServerRequest> requestById = new HashMap<>();
	static HashMap<Integer, Long> requestByTask = new HashMap<>();
	static void testFullRandom() throws IOException, InterruptedException {
		long startTime = System.currentTimeMillis();
		System.err.println("Started random testing...");
		int N = 50;
		int tasks = 0;
		Thread listenerThread = new Thread(new Listener(socket.getInputStream(), N));
		listenerThread.start();
		for (int i = 0; i < N; i++) {
			ServerRequest request = formRandomRequest(tasks);
			if (request.hasSubmit()) {
				requestByTask.put(tasks, request.getRequestId());
				tasks++;
			}
			requests.add(request);
			requestById.put(request.getRequestId(), request);
			request.writeDelimitedTo(socket.getOutputStream());
			System.out.println(request);
		}
		System.err.println("All requests sent");
		listenerThread.join();
		System.err.println("Listener joined");
		long endTime = System.currentTimeMillis();
		
		for (ServerResponse r : responses) {
			System.out.println(r);
		}
		System.err.println("Started verification...");
		
		tasks = 0;
		for (ServerRequest r : requests) {
			if (r.hasSubmit()) {
				resultsByTask.put(tasks, evaluate(r.getSubmit().getTask()));
				tasks++;
			}
		}
		for (ServerResponse r : responses) {
			if (r.hasSubmitResponse()) {
				SubmitTaskResponse sr = r.getSubmitResponse();
				if (sr.getStatus().equals(Status.ERROR)) {
					System.err.println("Task not submitted!");
					return;
				}
			} else if (r.hasSubscribeResponse()) {
				SubscribeResponse sr = r.getSubscribeResponse();
				if (sr.getStatus().equals(Status.ERROR)) {
					System.err.println("Task not submitted!");
					return;
				}
				if (sr.getValue() != resultsByTask.get(requestById.get(r.getRequestId()).getSubscribe().getTaskId())) {
					System.err.println("Wrong result!");
					return;
				}
			}
		}
		System.err.println("Verification completed!");
		long verTime = System.currentTimeMillis();
		System.err.println("Communication: " + (endTime - startTime) /1000.0 + "s");
		System.err.println("Verification: " + (verTime - endTime) /1000.0 + "s");
	}
	static long evalParam(Param p) {
		if (p.hasValue())
			return p.getValue();
		else {
			return resultsByTask.get(p.getDependentTaskId());
		}
	}
	static long evaluate(Task task) {
		long a = evalParam(task.getA());
		long b = evalParam(task.getB());
		long p = evalParam(task.getP());
		long m = evalParam(task.getM());
		long n = task.getN();
		while (n-- > 0) {
			b = (a * p + b) % m;
			a = b;
		}
		return a;
	}
	static class Listener implements Runnable {
		InputStream in;
		int N;

		public Listener(InputStream in, int N) {
			this.in = in;
			this.N = N;
		}
		public void run() {
			while (true) {
				try {
					ServerResponse response = ServerResponse.parseDelimitedFrom(in);
					if (response == null)
						break;
					responses.add(response);
					if (responses.size() >= N)
						break;
				} catch (IOException e) {
					break;
				}
			}
		}
	}
	static void testDependent() throws IOException {
		ServerRequest request;
		ServerResponse response;
		int N = 2;
		long a = 1, b = 0, p = 1, m = 23, n = 1000000000;
		Param pa = pureParam(a);
		Param pb = pureParam(b);
		Param pp = pureParam(p);
		Param pm = pureParam(m);
		ServerResponse[] responses = new ServerResponse[N];
		ServerRequest[] requests = new ServerRequest[N];
		requests[0] = formSubmitTaskRequest(pa, pb, pp, pm, n);
		long[] expectedResult = {6, 13};
		
		
		for (int i = 0; i < N; i++) {
			request = requests[i];
			request.writeDelimitedTo(socket.getOutputStream());
			response = ServerResponse.parseDelimitedFrom(socket.getInputStream());
			responses[i] = response;
			System.err.println(response);
			if (i < N - 1) {
				requests[i + 1] = formSubmitTaskRequest(dependentParam(response.getSubmitResponse().getSubmittedTaskId()), pb, pp, pm, n); 
			}
		}
				
		request = formListTasksRequest();
		request.writeDelimitedTo(socket.getOutputStream());
		response = ServerResponse.parseDelimitedFrom(socket.getInputStream());
		System.err.println(response);
		
		for (int i = 0; i < N; i++) {
			int taskId = responses[i].getSubmitResponse().getSubmittedTaskId();
			request = formSubscribeRequest(taskId);
			request.writeDelimitedTo(socket.getOutputStream());
			response = ServerResponse.parseDelimitedFrom(socket.getInputStream());
			long res = response.getSubscribeResponse().getValue();
			System.err.println("Result " + taskId + ": " + res);
			if (res != expectedResult[i]) {
				throw new AssertionError(); 
			}
		}
	}
	static void testConsecutive() throws IOException {
		ServerRequest request;
		ServerResponse response;
		int N = 3;
		long a = 1, b = 0, p = 1, m = 23, n = 1000000000, expectedResult = 6;
		Param pa = pureParam(a);
		Param pb = pureParam(b);
		Param pp = pureParam(p);
		Param pm = pureParam(m);
		ServerResponse[] responses = new ServerResponse[N];
		
		for (int i = 0; i < N; i++) {
			request = formSubmitTaskRequest(pa, pb, pp, pm, n);
			request.writeDelimitedTo(socket.getOutputStream());
			response = ServerResponse.parseDelimitedFrom(socket.getInputStream());
			responses[i] = response;
			System.err.println(response);
		}
				
		request = formListTasksRequest();
		request.writeDelimitedTo(socket.getOutputStream());
		response = ServerResponse.parseDelimitedFrom(socket.getInputStream());
		System.err.println(response);
		
		for (int i = 0; i < N; i++) {
			int taskId = responses[i].getSubmitResponse().getSubmittedTaskId();
			request = formSubscribeRequest(taskId);
			request.writeDelimitedTo(socket.getOutputStream());
			response = ServerResponse.parseDelimitedFrom(socket.getInputStream());
			long res = response.getSubscribeResponse().getValue();
			System.err.println("Result " + taskId + ": " + res);
			if (res != expectedResult) {
				throw new AssertionError(); 
			}
		}
	}
	static Param pureParam(long a) {
		return Param.newBuilder().setValue(a).build();
	}
	static Param dependentParam(int id) {
		return Param.newBuilder().setDependentTaskId(id).build();
	}
	static ServerRequest formSubmitTaskRequest(Param a, Param b, Param p, Param m, long n) {
		Task task = Task.newBuilder()
				.setA(a)
				.setB(b)
				.setP(p)
				.setM(m)
				.setN(n).build();
		SubmitTask submitTask = SubmitTask.newBuilder().setTask(task).build();
		return ServerRequest.newBuilder().setClientId(clientId).setRequestId(curId++).setSubmit(submitTask).build();
	}
	static ServerRequest formSubscribeRequest(int taskId) {
		Subscribe subscribe = Subscribe.newBuilder().setTaskId(taskId).build();
		return ServerRequest.newBuilder().setClientId(clientId).setRequestId(curId++).setSubscribe(subscribe).build();
	}
	static ServerRequest formListTasksRequest() {
		ListTasks listTasks = ListTasks.newBuilder().build();
		return ServerRequest.newBuilder().setClientId(clientId).setRequestId(curId++).setList(listTasks).build();
	}
	static Param randomParam(int tasks) {
		if (tasks == 0 || random.nextInt(20) != 0) {
			return Param.newBuilder().setValue(random.nextLong()).build();
		} else {
			return Param.newBuilder().setDependentTaskId((random.nextInt(tasks))).build();
		}
	}
	static ServerRequest formRandomRequest(int tasks) {
		int type = random.nextInt(3);
		if (type == 0) {
			Param a = randomParam(tasks);
			Param b = randomParam(tasks);
			Param p = randomParam(tasks);
			Param m = randomParam(tasks);
			long n = random.nextLong() % MAXN;
			return formSubmitTaskRequest(a, b, p, m, n);
		} else if (type == 1 && tasks > 0) {
			return formSubscribeRequest(random.nextInt(tasks));
		} else {
			return formListTasksRequest();
		}
	}
}