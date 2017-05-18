package communication;

public class Common {

    public static void printTaskInfo(Protocol.ServerRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append("Server: Request id: " + request.getRequestId());
        sb.append(" Client id: " + request.getClientId());


        if (request.hasList()) {
            sb.append(" Type: getlist");
        } else if (request.hasSubmit()) {
            sb.append(" Type: submit\n");
            printTask(sb, request.getSubmit().getTask());
        } else if (request.hasSubscribe()) {
            sb.append(" Type: subscribe " + request.getSubscribe().getTaskId());
        } else {
            sb.append(" Unknown type of request");
        }
        System.out.println(sb.toString());
    }

    public static void printTaskRepsonse(Protocol.ServerResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Server: Request id: " + response.getRequestId());

        if (response.hasListResponse()) {
            int countTask = response.getListResponse().getTasksCount();
            sb.append(" Tasks on the server: \n");
            for (int iTask = 0; iTask < countTask; iTask++) {
                Protocol.ListTasksResponse.TaskDescription task = response.getListResponse().getTasks(iTask);
                if (task.hasResult()) {
                    sb.append(task.getTaskId() +  " - " + task.getResult());
                } else {
                    sb.append(task.getTaskId() + " - NA");
                }
                sb.append(" || ");
            }
        } else if (response.hasSubmitResponse()) {
            sb.append(" Task " + response.getSubmitResponse().getSubmittedTaskId() + " was submitted");
        } else if (response.hasSubscribeResponse()) {
            if (response.getSubscribeResponse().getStatus() == Protocol.Status.ERROR) {
                sb.append(" Not calculated for request " + response.getRequestId());
            } else {
                sb.append(" Result is " + response.getSubscribeResponse().getValue() + " for request " + response.getRequestId());
            }
        } else {
            sb.append("Unknown type of request");
        }
        System.out.println(sb.toString());
    }

    public static void printParam(StringBuilder sb, String paramValue, Protocol.Task.Param param) {
        if (param.hasDependentTaskId()) {
            sb.append(paramValue + " depends on " + param.getDependentTaskId());
        } else {
            sb.append(paramValue + " = " + param.getValue());
        }
    }

    public static void printTask(StringBuilder sb, Protocol.Task task) {
        printParam(sb, "A", task.getA());
        printParam(sb, ", B", task.getB());
        printParam(sb, ", P", task.getP());
        printParam(sb, ", M", task.getM());
        sb.append(", N = " + task.getN());
    }
}
