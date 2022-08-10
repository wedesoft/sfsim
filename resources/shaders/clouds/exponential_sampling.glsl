#version 410 core

int number_of_steps(float a, float b, int max_samples, float min_step)
{
  return min(int(ceil(log(b / a) / log(min_step))), max_samples);
}

float step_size(float a, float b, int num_steps)
{
  return pow(b / a, 1.0 / num_steps);
}

float next_point(float p, float step_size)
{
  return p * step_size;
}
