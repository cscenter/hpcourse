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

std::random_device rd;
std::mt19937 rng(rd());

int value;
int active_threads = 0;
int cons_count = 0;
int sleep_time = 0;

bool ready = false;
bool end = false;
bool done = true;

pthread_mutex_t data_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  val_cond = PTHREAD_COND_INITIALIZER;
pthread_cond_t  done_cond = PTHREAD_COND_INITIALIZER;

pthread_mutex_t start_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  start_cond = PTHREAD_COND_INITIALIZER;

pthread_key_t error_code;

int get_last_error() {
  return (*((int *)pthread_getspecific(error_code)));
}

void set_last_error(int code) {
  int* error_code_ptr = (int*)pthread_getspecific(error_code);
  if(error_code_ptr == 0) {
      error_code_ptr = new int();
      pthread_setspecific(error_code, error_code_ptr);
  }
  *error_code_ptr = code;
}

void free_tls(void *value) {
    delete((int*)value);
    pthread_setspecific(error_code, NULL);
}

bool check_overflow(int sum, int value) {
  return value > 0 && sum > INT32_MAX - value || value < 0 && sum < INT32_MIN - value;
}

void* producer_routine(void* arg) {
  pthread_mutex_lock(&start_mutex);
  if (active_threads == 0) {
    pthread_cond_wait(&start_cond, &start_mutex);
  }
  pthread_mutex_unlock(&start_mutex);

  int num;
  while (std::cin >> num) {
    pthread_mutex_lock(&data_mutex);

    while(!done) {
      pthread_cond_wait(&done_cond, &data_mutex);
    }

    done = false;
    value = num;
    ready = true;

    if (active_threads == 0) {
      pthread_mutex_unlock(&data_mutex);
      break;
    }

    pthread_cond_signal(&val_cond);
    pthread_mutex_unlock(&data_mutex);
  }

  pthread_mutex_lock(&data_mutex);
  end = true;
  pthread_cond_broadcast(&val_cond);
  pthread_mutex_unlock(&data_mutex);
}
 
void* consumer_routine(void* arg) {
  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
  set_last_error(NOERROR);

  std::uniform_int_distribution<int> uni(0, sleep_time);

  pthread_mutex_lock(&start_mutex);
  active_threads++;
  pthread_cond_signal(&start_cond);
  pthread_mutex_unlock(&start_mutex);

  int *data = (int *) arg;
  int local_sum = 0;

  while (true) {
    pthread_mutex_lock(&data_mutex);

    while (!ready && !end) {
      pthread_cond_wait(&val_cond, &data_mutex);
    }

    if (!ready && end) {
      active_threads--;
      pthread_mutex_unlock(&data_mutex);
      break;
    }

    ready = false;
    done = true;

    if (check_overflow(local_sum, *(data))) {
      set_last_error(OVERFLOW_ERROR);
      active_threads--;
      pthread_cond_signal(&done_cond);
      pthread_mutex_unlock(&data_mutex);
      break;
    }
    local_sum += *(data);

    pthread_cond_signal(&done_cond);
    pthread_mutex_unlock(&data_mutex);

    std::this_thread::sleep_for(std::chrono::milliseconds(uni(rng)));
  }

  Result *res = (Result *) malloc(sizeof(Result));
  res->sum = local_sum;
  res->error_code = get_last_error();
  pthread_exit(res);
}
 
void* consumer_interruptor_routine(void* arg) {
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

  pthread_key_create(&error_code, free_tls);

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

  pthread_mutex_destroy(&start_mutex);
  pthread_mutex_destroy(&data_mutex);
  pthread_cond_destroy(&start_cond);
  pthread_cond_destroy(&val_cond);
  pthread_cond_destroy(&done_cond);

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
