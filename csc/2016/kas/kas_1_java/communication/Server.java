package communication;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import communication.Protocol.ListTasksResponse;
import communication.Protocol.ListTasksResponse.TaskDescription;
import communication.Protocol.ServerRequest;
import communication.Protocol.ServerResponse;
import communication.Protocol.Status;
import communication.Protocol.SubmitTaskResponse;
import communication.Protocol.SubscribeResponse;
import communication.Protocol.Task;
import communication.Protocol.Task.Param;

public class Server {
	private static final int DEFAULT_PORT = 1036;
	
	public static void main(String[] args) throws IOException {
		int port = DEFAULT_PORT;
		if (args.length >= 1) {
			port = Integer.parseInt(args[0].trim());
		}
		//System.err.println("Port " + port);
		ServerSocket socket = new ServerSocket(port);
		while (true) {
			System.err.println("Waiting for connection...");
			TaskServer taskServer = new TaskServer(socket.accept());
			taskServer.listen();
			System.err.println("Connected!");
		}
	}
	static Map<Integer, TaskDescription> byId = Collections.synchronizedMap(new HashMap<Integer, TaskDescription>());
	static AtomicInteger curId = new AtomicInteger();
	static class TaskServer {
		public TaskServer(Socket socket) {
			this.connectionSocket = socket;
		}
		Socket connectionSocket;
		public void listen() throws IOException {
			System.err.println("Listener thread started");
			while (true) {
				try {
					ServerRequest request = ServerRequest.parseDelimitedFrom(connectionSocket.getInputStream());
					if (request == null)
						break;
					System.err.println("Request received!");
					if (request.hasSubmit()) {
						int taskId = curId.getAndIncrement();
						Task task = request.getSubmit().getTask();
						TaskDescription.Builder builder = TaskDescription.newBuilder().setClientId(request.getClientId())
								.setTaskId(taskId).setTask(task);
						ServerResponse response;
						if (!correctSubmitTaskRequest(request)) {
							response = ServerResponse.newBuilder().setRequestId(request.getRequestId())
									.setSubmitResponse(SubmitTaskResponse.newBuilder().setSubmittedTaskId(-1).setStatus(Status.ERROR)).build();
						} else {
							byId.put(taskId, builder.build());
							(new Thread(new TaskStarter(request, taskId))).start();
							response = ServerResponse.newBuilder().setRequestId(request.getRequestId())
									.setSubmitResponse(SubmitTaskResponse.newBuilder().setSubmittedTaskId(taskId).setStatus(Status.OK)).build();
						}
						
						synchronized (connectionSocket) {
							response.writeDelimitedTo(connectionSocket.getOutputStream());
							//connectionSocket.getOutputStream().flush();
							//System.err.println("Submitted: " + response);
						}
					} else if (request.hasSubscribe()) {
						(new Thread(new TaskSubscriber(request))).start();
					} else if (request.hasList()) {
						ListTasksResponse.Builder builder = ListTasksResponse.newBuilder().setStatus(Status.OK);
						synchronized(byId) {
							for (TaskDescription desc : byId.values()) {
								builder.addTasks(desc);
							}
						}
						ServerResponse response = ServerResponse.newBuilder().setListResponse(builder.build()).setRequestId(request.getRequestId()).build();
						synchronized (connectionSocket) {
							response.writeDelimitedTo(connectionSocket.getOutputStream());
						}
					} else {
						System.err.println("Invalid request.");
					}
					
				} catch (Exception e) {
					break;
				}
			}
		}
		boolean correctParam(Param param) {
			if (param.hasValue()) {
				return true;
			} else {
				return byId.containsKey(param.getDependentTaskId());
			}
		}
		boolean correctSubmitTaskRequest(ServerRequest request) {
			Task task = request.getSubmit().getTask();
			Param[] params = {task.getA(), task.getB(), task.getP(), task.getM()};
			for (Param param : params) {
				if (!correctParam(param)) {
					return false;
				}
			}
			return true;
		}
		class TaskSubscriber implements Runnable {
			ServerRequest request;
			
			public TaskSubscriber(ServerRequest request) {
				this.request = request;
			}

			public void run() {
				int taskId = request.getSubscribe().getTaskId();
				SubscribeResponse subscribeResponse;
				TaskDescription destTask = byId.get(taskId);
				if (destTask == null) {
					subscribeResponse = SubscribeResponse.newBuilder().setStatus(Status.ERROR).build();
				} else {
					try {
						long val;
						Task task = destTask.getTask();
						synchronized (task) {
							destTask = byId.get(taskId);
							if (destTask.hasResult()) {
								val = destTask.getResult();
							} else {
								task.wait();
								val = byId.get(taskId).getResult();
							}
						}
						subscribeResponse = SubscribeResponse.newBuilder().setStatus(Status.OK).setValue(val).build(); 
					} catch (InterruptedException e) {
						subscribeResponse = SubscribeResponse.newBuilder().setStatus(Status.ERROR).build();
					}
				}
				ServerResponse response = ServerResponse.newBuilder().setRequestId(request.getRequestId())
					.setSubscribeResponse(subscribeResponse).build();
				synchronized (connectionSocket) {
					try {
						response.writeDelimitedTo(connectionSocket.getOutputStream());
					} catch (IOException e) {}
				}
			}
		}
		class TaskStarter implements Runnable {
			Task task;
			ServerRequest request;
			int id;
			
			public TaskStarter(ServerRequest request, int id) {
				this.request = request;
				this.task = request.getSubmit().getTask();
				this.id = id;
			}
			private Param reduceParam(int id) throws InterruptedException {
				TaskDescription destTask = byId.get(id);
				long val;
				Task task = destTask.getTask();
				synchronized (task) {
					destTask = byId.get(id);
					if (destTask.hasResult()) {
						val = destTask.getResult();
					} else {
						do {
							task.wait();
						} while (!byId.get(id).hasResult());
						val = byId.get(id).getResult();
					}
				}
				return Param.newBuilder().setValue(val).build();
			}
			private Task reduceTask() throws InterruptedException {
				Task.Builder builder = Task.newBuilder(task);
				if (task.getA().hasDependentTaskId()) {
					builder.setA(reduceParam(task.getA().getDependentTaskId()));
				}
				if (task.getB().hasDependentTaskId()) {
					builder.setB(reduceParam(task.getB().getDependentTaskId()));
				}
				if (task.getP().hasDependentTaskId()) {
					builder.setP(reduceParam(task.getP().getDependentTaskId()));
				}
				if (task.getM().hasDependentTaskId()) {
					builder.setM(reduceParam(task.getM().getDependentTaskId()));
				}
				return builder.build();
			}
			private long runTask(Task task) {
				long a = task.getA().getValue();
				long b = task.getB().getValue();
				long p = task.getP().getValue();
				long m = task.getM().getValue();
				long n = task.getN();
				while (n-- > 0) {
					b = (a * p + b) % m;
					a = b;
				}
				return a;
			}
			public void run() {
				try {
					synchronized (task) {
						Task reducedTask = reduceTask();
						long res = runTask(reducedTask);
						TaskDescription.Builder builder = TaskDescription.newBuilder().setClientId(request.getClientId())
								.setTaskId(id).setTask(task);
						builder.setResult(res);
						byId.put(id, builder.build());
						task.notifyAll();
					}
				} catch (InterruptedException e) {
				}
			}
		}
	}
}



