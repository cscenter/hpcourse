//!
//! @file solution.cpp
//! @brief Program to calculate sum of passed numbers using producer-consumer pattern.
//!

#include <pthread.h>
#include <iostream>
#include <cstdlib>
#include <vector>
#include <cstring>
#include <unistd.h>

/**
 * Count of consumer threads.
 */
int consumer_cnt = 0;

/**
 * Sleep max duration (ms).
 */
int max_sleep_duration = 0;

/**
 *  Shared value to increment local sum.
 */
int shared_value = 0;

/**
 * Shared value change trigger.
 */
bool value_changed = false;

/**
 * Producer thread status.
 */
bool producer_running = false;

/**
 * Consumer status code, means no error happen.
 */
#define NOERROR 0

/**
 * Consumer status code, means overflow happen.
 */
#define OVERFLOW 1

/**
 * TLS variable for storing each thread status.
 */
thread_local int thread_status = NOERROR;

/**
 * Represents a single consumer thread data.
 */
struct ConsumerData
{
  /**
   * Shared value.
   */
  int *shared_data;

  /**
   * Consumer status.
   */
  int status_code;

  /**
   * Aggregated value.
   */
  int aggregated_val;
};

pthread_mutex_t producer_consumer_lock = PTHREAD_MUTEX_INITIALIZER;

/**
 * New value passed by producer to consumer.
 */
pthread_cond_t accumulation_scheduled = PTHREAD_COND_INITIALIZER;

/**
 * New value processed by consumer.
 */
pthread_cond_t accumulation_processed= PTHREAD_COND_INITIALIZER;

/**
 * Started consumer counter.
 */
int started_consumers = 0;
pthread_mutex_t consumer_start_lock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t consumers_started = PTHREAD_COND_INITIALIZER;


/**
 * Gets the thread status.
 */
int get_last_error()
{
  return thread_status;
}

/**
 * Sets the consumer status.
 * @param code, the new status code, should be 0 or 1.
 */
void set_last_error(int code)
{
  thread_status = code;
}

/**
 * Checks for overflow and accumulates data.
 * If overflow happen - thread status would be updated to OVERFLOW.
 * @param sum - value to accumulate.
 * @param inc - value to add.
 * @return false if overflow happen, true otherwise.
 */
bool safe_accumulation(int &sum, int inc)
{
  if ((sum > 0 && inc > 0 && sum + inc < sum) || (sum < 0 && inc < 0 && sum + inc > sum))
  {
    set_last_error(OVERFLOW);
    return false;
  }
  sum += inc;
  return true;
}

/**
 * Reads numbers from stdin and sends them to consumers.
 *
 * @param arg shared variable for notification.
 */
void* producer_routine(void* arg)
{
  int* shared_value = static_cast<int*>(arg);

  int val;
  producer_running = true;
  while (std::cin >> val)
  {
    //std::cout << "Sending val=" << val << std::endl;
    pthread_mutex_lock(&producer_consumer_lock);

    // wait until accumulation processed
    while (value_changed)
    {
      pthread_cond_wait(&accumulation_processed, &producer_consumer_lock);
    }

    *shared_value = val;
    value_changed = true;
    pthread_cond_signal(&accumulation_scheduled);

    pthread_mutex_unlock(&producer_consumer_lock);
    //std::cout << "Sent val=" << val << std::endl;
  }
  //std::cout << "All data pushed" << std::endl;

  // wait for the last value to process
  pthread_mutex_lock(&producer_consumer_lock);
  while (value_changed) {
    pthread_cond_wait(&accumulation_processed, &producer_consumer_lock);
  }

  value_changed = true;

  // producer finished
  producer_running = false;
  pthread_cond_broadcast(&accumulation_scheduled);

  pthread_mutex_unlock(&producer_consumer_lock);
}

/**
 * Consumer thread.
 *
 * @param arg. Consumer initial data.
 * @return null, all data stored in predefined vector.
 */
void* consumer_routine(void* arg)
{
  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

  pthread_mutex_lock(&consumer_start_lock);
  started_consumers++;
  pthread_cond_signal(&consumers_started);
  pthread_mutex_unlock(&consumer_start_lock );

  ConsumerData *data = static_cast<ConsumerData*>(arg);
  //std::cout << "Consumer started" << std::endl;

  while (true)
  {
    pthread_mutex_lock(&producer_consumer_lock);

    while (!value_changed)
    {
      pthread_cond_wait(&accumulation_scheduled, &producer_consumer_lock);
    }

    if (!producer_running)
    {
      pthread_mutex_unlock(&producer_consumer_lock);
      data->status_code = get_last_error();
      pthread_exit(nullptr);
    }

    bool isOverflowed = !safe_accumulation(data->aggregated_val, *data->shared_data);
    //std::cout << "Shared data is=" << *data->shared_data << std::endl;
    value_changed = false;

    if (isOverflowed)
    {
      data->status_code = get_last_error();

      pthread_cond_signal(&accumulation_processed);
      pthread_mutex_unlock(&producer_consumer_lock);

      pthread_exit(nullptr);
    }

    pthread_cond_signal(&accumulation_processed);
    pthread_mutex_unlock(&producer_consumer_lock);

    usleep(std::rand() % (max_sleep_duration + 1) * 1000);
  }
}

/**
 * Waits for consumers to start and interrupts random consumer.
 *
 * @param arg. Vector of consumer threads.
 */
void* consumer_interruptor_routine(void* arg)
{
  std::vector<pthread_t>* consumers = static_cast<std::vector<pthread_t>*>(arg);
  pthread_mutex_lock(&consumer_start_lock );

  // wait for consumers to start
  while (started_consumers < consumers->size())
  {
    pthread_cond_wait(&consumers_started, &consumer_start_lock);
  }
  pthread_mutex_unlock(&consumer_start_lock);

  // interrupt random consumer while producer is running
  while (producer_running)
  {
    std::size_t idx = std::rand() % (consumers->size());
    pthread_cancel((*consumers)[idx]);
  }
}

/**
 * Finishes passed threads.
 * @param threads_to_cleanup. Vector of started threads to join.
 */
void thread_cleanup(const std::vector<pthread_t> &threads_to_cleanup)
{
  for (std::size_t i = 0; i < threads_to_cleanup.size(); ++i)
  {
    pthread_join(threads_to_cleanup[i], nullptr);
  }
}

/**
 * Starts threads. Prints aggregated value to stdout or "overflow" if overflow happen.
 */
int run_threads()
{
  int sum = 0;
  pthread_t producer;
  pthread_t interruptor;
  std::vector<pthread_t> consumers(consumer_cnt);
  // consumer states
  std::vector<ConsumerData> consumers_data(consumer_cnt, {&shared_value, shared_value, NOERROR});

  int thread_operation_status;

  // init producer
  thread_operation_status = pthread_create(&producer, nullptr, producer_routine, &shared_value);
  if (thread_operation_status != 0)
  {
    // failed to create thread, there are no started threads yet.
    return 1;
  }

  // start N threads
  for (std::size_t i = 0; i < consumers.size(); ++i)
  {
    thread_operation_status = pthread_create(&consumers[i], nullptr, consumer_routine, (void*) &consumers_data[i]);
    if (thread_operation_status != 0)
    {
      // failed to create thread.
      // cleanup producer and started consumers
      std::vector<pthread_t> already_started {producer};
      for (std::size_t j = 0; j < i; ++j)
      {
        already_started.push_back(consumers[j]);
      }
      thread_cleanup(already_started);
      return 1;
    }
  }

  // init iterrputor
  thread_operation_status = pthread_create(&interruptor, nullptr, consumer_interruptor_routine, (void *) &consumers);
  if (thread_operation_status != 0)
  {
    // failed to create thread.
    // cleanup producer and started consumers
    std::vector<pthread_t> already_started(consumers.begin(), consumers.end());
    already_started.push_back(producer);
    thread_cleanup(already_started);
    return 1;
  }

  thread_operation_status = pthread_join(producer, nullptr);
  if (thread_operation_status != 0)
  {
    // failed to finish thread
    std::vector<pthread_t> already_started(consumers.begin(), consumers.end());
    already_started.push_back(interruptor);
    thread_cleanup(already_started);
    return 1;
  }
  thread_operation_status = pthread_join(interruptor, nullptr);
  if (thread_operation_status != 0)
  {
    // failed to finish thread
    std::vector<pthread_t> already_started(consumers.begin(), consumers.end());
    thread_cleanup(already_started);
    return 1;
  }

  // process consumers
  for (std::size_t i = 0; i < consumers.size(); ++i)
  {
    thread_operation_status = pthread_join(consumers[i], nullptr);
    if (thread_operation_status != 0)
    {
      // failed to finish thread
      std::vector<pthread_t> already_started(consumers.begin(), consumers.begin() + i);
      return 1;
    }

    // aggregate sum
    if (consumers_data[i].status_code == OVERFLOW || !safe_accumulation(sum, consumers_data[i].aggregated_val))
    {
      std::cout << "overflow" << std::endl;
      std::vector<pthread_t> already_started(consumers.begin(), consumers.end());
      return 1;
    }
  }

  // return aggregated sum of values
  std::cout << sum << std::endl;
  return 0;
}

int main(int argc, char **argv)
{
  if (argc == 2 && (std::strcmp(argv[1], "--help") == 0 || std::strcmp(argv[1], "-h") == 0))
  {
    std::cout << argv[0] << " [COUNT OF CONSUMERS] [MAX CONSUMER SLEEP DURATION] -- "
                         << "Program to calculate sum of passed numbers using producer-consumer pattern" << std::endl;
    std::cout << "\tCount of consumers must be > 0, sleep duration >= 0." << std::endl;
    std::cout << "\tFull HW description - https://my.compscicenter.ru/learning/assignments/53235/" << std::endl;
    return 0;
  }

  if (argc == 3)
  {
    consumer_cnt = std::atoi(argv[1]);
    max_sleep_duration = std::atoi(argv[2]);

    if (consumer_cnt > 0 && max_sleep_duration >= 0)
    {
      return run_threads();
    }
    // wrong input
    return 1;
  }
  // wrong input
  return 1;
}
