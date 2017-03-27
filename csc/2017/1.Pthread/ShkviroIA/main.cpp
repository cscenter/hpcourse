#pragma once

#include <iostream>
#include <vector>
#define HAVE_STRUCT_TIMESPEC
#include <pthread.h>

pthread_mutex_t value_mutex;

pthread_cond_t value_updated_cond;
pthread_cond_t value_read_cond;

class Value
{
public:
	Value();
	void update(int new_value);
	void check();
	int get() const;
	bool is_checked() const;
	bool is_finished() const;
	void set_finish();

private:
	int _data;
	bool _checked;
	bool _finish;
};

Value::Value()
	: _data(0)
	, _checked(true)
	, _finish(false)
{
}

void Value::update(int new_value)
{
	_data = new_value;
	_checked = false;
}

void Value::check()
{
	_checked = true;
}

int Value::get() const
{
	return _data;
}

bool Value::is_checked() const
{
	return _checked;
}

bool Value::is_finished() const
{
	return _finish;
}

void Value::set_finish()
{
	_finish = true;
}

struct ProducerArgs {
	ProducerArgs(Value* value_object, std::vector<int> number_list)
		: value_object(value_object)
		, number_list(number_list)
	{
	}

	Value* value_object;
	std::vector<int> number_list;
};

struct InterruptorArgs {
	InterruptorArgs(Value* value_object, pthread_t* to_interrupt)
		: value_object(value_object)
		, to_interrupt(to_interrupt)
	{
	}

	Value* value_object;
	pthread_t* to_interrupt;
};

void* producer_routine(void* args)
{
	ProducerArgs* producer_args = (ProducerArgs*) args;
	Value* value_object = producer_args->value_object;
	std::vector<int> number_list = producer_args->number_list;
	int number_count = number_list.size();
	for (int i = 0; i < number_count; ++i) {
		pthread_mutex_lock(&value_mutex);
		value_object->update(number_list[i]);
		if (i == number_count - 1) {
			value_object->set_finish();
		}
		pthread_cond_signal(&value_updated_cond);

		while (!value_object->is_checked()) {
			pthread_cond_wait(&value_read_cond, &value_mutex);			
		}		
		pthread_mutex_unlock(&value_mutex);
	}
	return 0;
}

void* consumer_routine(void* value_object_void)
{
	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
	Value* value_object = (Value*) value_object_void;
	int* res = new int(0);
	while (!value_object->is_finished()) {
		pthread_mutex_lock(&value_mutex);
		while (value_object->is_checked()) {
			pthread_cond_wait(&value_updated_cond, &value_mutex);
		}
		
		*res += value_object->get();
		value_object->check();
		pthread_cond_signal(&value_read_cond);
		pthread_mutex_unlock(&value_mutex);		
	}
	return (void*)res;
}

void* interruptor_routine(void* args)
{
	InterruptorArgs* interruptor_args = (InterruptorArgs*)args;
	Value* value_object = interruptor_args->value_object;
	pthread_t* to_interrupt_id = interruptor_args->to_interrupt;
	while (!value_object->is_finished()) {
		if (pthread_cancel(*to_interrupt_id) == 1) {
			std::cout << "Interruptor win!" << '\n';
			return 0;
		}
	}
	return 0;
}

int run_threads(std::vector<int> number_list)
{
	Value value_object;

	pthread_t producer_id;
	pthread_t consumer_id;
	pthread_t interruptor_id;

	pthread_mutex_init(&value_mutex, NULL);
	pthread_cond_init(&value_read_cond, NULL);
	pthread_cond_init(&value_updated_cond, NULL);

	ProducerArgs* producer_args = new ProducerArgs(&value_object, number_list);
	pthread_create(&producer_id, NULL, producer_routine, (void *) producer_args);

	int* consumer_res = new int(0);
	pthread_create(&consumer_id, NULL, consumer_routine, (void *) &value_object);
	InterruptorArgs* interruptor_args = new InterruptorArgs(&value_object, &producer_id);
	pthread_create(&interruptor_id, NULL, interruptor_routine, (void *) &interruptor_args);

	pthread_join(producer_id, NULL);	
	pthread_join(consumer_id, (void**)&consumer_res);
	pthread_join(interruptor_id, NULL);
	int result = *consumer_res;

	pthread_cond_destroy(&value_read_cond);
	pthread_cond_destroy(&value_updated_cond);
	pthread_mutex_destroy(&value_mutex);

	delete producer_args;
	delete consumer_res;
	
	return result;
}

std::vector<int> get_number_list()
{
	std::vector<int> number_list;
	int cur_value;

	while (std::cin >> cur_value) {
		number_list.push_back(cur_value);
	}
	return number_list;
}

int main(int argc, char ** argv)
{
	std::vector<int> number_list = get_number_list();

	int consumer_res = run_threads(number_list);
	std::cout << consumer_res << '\n';
	return 0;
}
