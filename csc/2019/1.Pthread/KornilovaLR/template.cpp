#include <pthread.h>
#include <iostream>
#include <vector>
#include <sstream>
#include <unistd.h>


#define NOERROR 0
#define OVERFLOW 1

unsigned         sleep_time_ms;
pthread_key_t    error_code_key;
unsigned         n;
std::atomic<int> consumer_thread_started(0);
pthread_cond_t   consumer_started_cond  = PTHREAD_COND_INITIALIZER;
pthread_cond_t   update_ready_cond      = PTHREAD_COND_INITIALIZER;
pthread_cond_t   update_processed_cond  = PTHREAD_COND_INITIALIZER;
pthread_mutex_t  consumer_started_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t  update_mutex           = PTHREAD_MUTEX_INITIALIZER;

struct update {
  const int value;
  const bool processed;
};

struct result {
  const int sum;
  const int error_code;
};

struct interruptor_input {
  const unsigned n;
  const pthread_t *consumer_threads;
  std::atomic<update *> *update_ptr;
};

int get_last_error() {
  int *code = (int *) pthread_getspecific(error_code_key);
  return code == nullptr ? NOERROR : *code;
}


void set_last_error(int code) {
  int *prev_code = (int *) pthread_getspecific(error_code_key);
  pthread_setspecific(error_code_key, new int(code));
  delete prev_code;
}

update *do_update(std::atomic<update *> &update_ptr, update *u, bool broadcast) {
  pthread_mutex_lock(&update_mutex);
  update *current_update = update_ptr.load();
  while (!current_update->processed) {
    pthread_cond_wait(&update_processed_cond, &update_mutex);
    current_update = update_ptr.load();
  }
  update_ptr.store(u);

  if (broadcast) pthread_cond_broadcast(&update_ready_cond);
  else pthread_cond_signal(&update_ready_cond);

  pthread_mutex_unlock(&update_mutex);
  return current_update;
}

/**
 * wait for consumer to start
 * read data, loop through each value and update the value, notify consumer, wait for consumer to process
 */
void *producer_routine(void *upd) {
  // wait for consumers
  pthread_mutex_lock(&consumer_started_mutex);
  while (consumer_thread_started.load() != n) {
    pthread_cond_wait(&consumer_started_cond, &consumer_started_mutex);
  }
  pthread_mutex_unlock(&consumer_started_mutex);

  std::atomic<update *> &update_ptr = *((std::atomic<update *> *) upd);
  std::string str;
  getline(std::cin, str);
  std::istringstream iss(str);
  int number;
  while (iss >> number) {
    update *processed_update = do_update(update_ptr, new update{number, false}, false);
    delete processed_update;
  }
  update *processed_update = do_update(update_ptr, nullptr, true);
  delete processed_update;
  pthread_exit(nullptr);
}


struct timespec *get_timespec(unsigned time_ms) {
  if (time_ms == 0) return nullptr;
  auto *ts_sleep = new struct timespec;
  ts_sleep->tv_sec = time_ms / 1000;
  ts_sleep->tv_nsec = (time_ms % 1000) * 1000 * 1000;
  return ts_sleep;
}


void maybe_sleep() {
  if (sleep_time_ms == 0) return;
  struct timespec *ts = get_timespec(rand() % ((int) sleep_time_ms));
  if (ts != nullptr) {
    nanosleep(ts, nullptr);
    delete ts;
  }
}

int update_sum(int a, int b) {
  int sum = a + b;
  if (b >= 0 && sum >= a) return sum;
  if (b < 0 && sum < a) return sum;
  set_last_error(OVERFLOW);
  return sum;
}

/**
 * notify about start
 * for every update issued by producer, read the value and add to sum
 * return pointer to result (for particular consumer)
 */
void *consumer_routine(void *upd) {
  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

  // notify
  pthread_mutex_lock(&consumer_started_mutex);
  consumer_thread_started++;
  pthread_cond_signal(&consumer_started_cond);
  pthread_mutex_unlock(&consumer_started_mutex);

  std::atomic<update *> &update_ptr = *((std::atomic<update *> *) upd);
  int local_sum = 0;
  while (true) {
    pthread_mutex_lock(&update_mutex);
    update *current_update = update_ptr.load();
    while (current_update != nullptr && current_update->processed) {
      pthread_cond_wait(&update_ready_cond, &update_mutex);
      current_update = update_ptr.load();
    }
    if (current_update == nullptr) {
      pthread_mutex_unlock(&update_mutex);
      pthread_exit(new result{local_sum, get_last_error()});
    }
    update_ptr.store(new update{current_update->value, true});
    pthread_cond_signal(&update_processed_cond);
    pthread_mutex_unlock(&update_mutex);

    local_sum = update_sum(local_sum, current_update->value);
    delete current_update;
    if (get_last_error() != NOERROR) pthread_exit(new result{local_sum, get_last_error()});
    maybe_sleep();
  }
}

/**
 * wait for consumers to start
 * interrupt random consumer while producer is running
 */
void *consumer_interruptor_routine(void *arg) {
  pthread_mutex_lock(&consumer_started_mutex);
  while(consumer_thread_started.load() != n) {
    pthread_cond_wait(&consumer_started_cond, &consumer_started_mutex);
  }
  pthread_mutex_unlock(&consumer_started_mutex);

  auto *input = (interruptor_input *) arg;
  while (input->update_ptr->load() != nullptr) {
    pthread_cancel(input->consumer_threads[rand() % input->n]);
  }
  delete input;
  pthread_exit(nullptr);
}

int start_consumer_threads(pthread_t *consumer_threads, std::atomic<update *> *update_ptr) {
  for (int i = 0; i < n; i++) {
    pthread_t thread;
    int code = pthread_create(&thread, nullptr, consumer_routine, update_ptr);
    if (code != 0) {
      std::cout << "error code: " << code << std::endl;
      return code;
    }
    consumer_threads[i] = thread;
  }
  return 0;
}

int start_interruptor_thread(pthread_t &interruptor_thread, pthread_t *consumer_threads,
                             std::atomic<update *> *update_ptr) {
  auto *input = new interruptor_input{n, consumer_threads, update_ptr};
  int code = pthread_create(&interruptor_thread, nullptr, consumer_interruptor_routine, input);
  if (code != 0) std::cout << "error code: " << code << std::endl;
  return code;
}

int start_producer_thread(pthread_t &producer_thread, std::atomic<update *> *update_ptr) {
  int res = pthread_create(&producer_thread, nullptr, producer_routine, update_ptr);
  if (res != 0) {
    std::cout << "error code: " << res << std::endl;
    return res;
  }
  return 0;
}

result get_result(pthread_t *consumer_threads) {
  int sum = 0;
  for (int i = 0; i < n; i++) {
    void *res_ptr = nullptr;
    pthread_join(consumer_threads[i], &res_ptr);
    assert(res_ptr != PTHREAD_CANCELED);
    int local_sum = ((result *) res_ptr)->sum;
    int error_code = ((result *) res_ptr)->error_code;
    delete (result *) res_ptr;
    if (error_code != NOERROR) return {sum, error_code};
    sum = update_sum(sum, local_sum);
    if (get_last_error() != NOERROR) return {sum, get_last_error()};
  }
  return {sum, NOERROR};
}

/**
 * start N threads and wait until they're done
 * return aggregated sum of values
 */
int run_threads(int &sum) {
  pthread_key_create(&error_code_key, nullptr);
  std::atomic<update *> update_ptr;
  update_ptr.store(new update{0, true});

  auto *consumer_threads = new pthread_t[n];
  int code = start_consumer_threads(consumer_threads, &update_ptr);
  if (code != 0) return code;

  pthread_t interruptor_thread;
  code = start_interruptor_thread(interruptor_thread, consumer_threads, &update_ptr);
  if (code != 0) return code;

  pthread_t producer_thread;
  code = start_producer_thread(producer_thread, &update_ptr);
  if (code != 0) return code;

  result result = get_result(consumer_threads);
  delete[] consumer_threads;

  pthread_cond_destroy(&consumer_started_cond);
  pthread_cond_destroy(&update_ready_cond);
  pthread_cond_destroy(&update_processed_cond);
  pthread_mutex_destroy(&consumer_started_mutex);
  pthread_mutex_destroy(&update_mutex);

  sum = result.sum;
  return result.error_code;
}

int main(int argc, char *argv[]) {
  std::stringstream arg1(argv[1]);
  std::stringstream arg2(argv[2]);

  arg1 >> n;
  arg2 >> sleep_time_ms;
  int sum = 0;
  int error_code = run_threads(sum);
  if (error_code == NOERROR) {
    std::cout << sum << std::endl;
    return 0;
  } else {
    std::cout << "overflow" << std::endl;
    return 1;
  }
}
