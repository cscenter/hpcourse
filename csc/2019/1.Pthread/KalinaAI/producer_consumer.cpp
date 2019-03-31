#include <pthread.h>
#include <iostream>
#include <sstream>
#include <random>
#include <chrono>
#include <thread>

#define NOERROR 0
#define OVERFLOW_ERROR 1

struct Result {
	int sum;
	int error_code;
};

int value;
int active_threads = 0;
int cons_count = 0;
int sleep_time = 0;

bool ready = false;
bool end = false;
pthread_mutex_t val_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  val_cond = PTHREAD_COND_INITIALIZER;

bool started = false;
pthread_mutex_t start_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  start_cond = PTHREAD_COND_INITIALIZER;

bool done = false;
pthread_mutex_t done_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  done_cond = PTHREAD_COND_INITIALIZER;

pthread_key_t error_code;

int get_last_error() {
  return (*((int *)pthread_getspecific(error_code)));
}

void set_last_error(int code) {
  pthread_setspecific(error_code, &code);
}

bool check_overflow(int sum, int value) {
  return sum > INT32_MAX - value;
}

void wait_start() {
  pthread_mutex_lock(&start_mutex);
  if (!started) {
    pthread_cond_wait(&start_cond, &start_mutex);
  }
  pthread_mutex_unlock(&start_mutex);
}

void work_done() {
  ready = false;
  pthread_mutex_unlock(&val_mutex);
  pthread_mutex_lock(&done_mutex);
  done = true;

  pthread_cond_signal(&done_cond);
  pthread_mutex_unlock(&done_mutex);
}

void* producer_routine(void* arg) {
  wait_start();

  int num;
  while (std::cin >> num && active_threads > 0)
  {
    pthread_mutex_lock(&val_mutex);
    value = num;
    ready = true;
    pthread_cond_signal(&val_cond);
    pthread_mutex_unlock(&val_mutex);

    pthread_mutex_lock(&done_mutex);
    while (!done && active_threads > 0) {
      pthread_cond_wait(&done_cond, &done_mutex);
    }
    done = false;
    pthread_mutex_unlock(&done_mutex);
  }

  pthread_mutex_lock(&val_mutex);
  end = true;
  pthread_cond_broadcast(&val_cond);
  pthread_mutex_unlock(&val_mutex);
}
 
void* consumer_routine(void* arg) {
  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
  set_last_error(NOERROR);

  pthread_mutex_lock(&start_mutex);
  started = true;
  active_threads++;
  pthread_cond_signal(&start_cond);
  pthread_mutex_unlock(&start_mutex);

  int *data = (int *) arg;
  int local_sum = 0;

  while (true) {
    pthread_mutex_lock(&val_mutex);
    while (!ready && !end) {
      pthread_cond_wait(&val_cond, &val_mutex);
    }

    if (end) {
      pthread_mutex_unlock(&val_mutex);
      break;
    }

    if (ready) {
      if (check_overflow(local_sum, *(data))) {
        set_last_error(OVERFLOW_ERROR);
        work_done();
        break;
      }
      local_sum += *(data);
    }

    work_done();

    std::this_thread::sleep_for(std::chrono::milliseconds(sleep_time));
  }
  active_threads--;

  Result *res = (Result *) malloc(sizeof(Result));
  res->sum = local_sum;
  res->error_code = get_last_error();
  pthread_exit(res);
}
 
void* consumer_interruptor_routine(void* arg) {
  wait_start();
  std::random_device rd;
  std::mt19937 rng(rd());
  std::uniform_int_distribution<int> uni(0, cons_count - 1);

  pthread_t *cons_id = (pthread_t *) arg;
  while(!end) {
    int index = uni(rng);
    pthread_cancel(cons_id[index]);
  }
}
 
int run_threads(int* result_code) {
  pthread_t prod_id;
  pthread_t cons_id[cons_count];
  pthread_t inter_id;

  pthread_key_create(&error_code, NULL);

  pthread_create(&prod_id, NULL, producer_routine, NULL);
  for (int i = 0; i < cons_count; i++) {
    pthread_create(&cons_id[i], NULL, consumer_routine, (void *) &value);
  }
  pthread_create(&inter_id, NULL, consumer_interruptor_routine, &cons_id);

  pthread_join(prod_id, NULL);
  pthread_join(inter_id, NULL);

  int result = 0;
  for (int i = 0; i < cons_count; i++) {
    Result *res;
    pthread_join(cons_id[i], (void**) &res);
    
    if ((*res).error_code != NOERROR) {
      *result_code = (*res).error_code;
    } else if (*result_code == NOERROR) {
      if (check_overflow(result, (*res).sum))
        *result_code = OVERFLOW_ERROR;
      else
        result += (*res).sum;
    }
    free(res);
  }

  return result;
}
 
int main(int argc, char *argv[]) {
  std::stringstream convert1(argv[1]);
  std::stringstream convert2(argv[2]);

  convert1 >> cons_count;
  convert2 >> sleep_time;

  int result_code = NOERROR;
  int result = run_threads(&result_code);
  if (result_code == NOERROR)
    std::cout << result << std::endl;
  else if (result_code == OVERFLOW_ERROR)
    std::cout << "overflow" << std::endl;

  return result_code;
}
