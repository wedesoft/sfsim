#version 410 core

int number_of_steps(float a, float b, int max_samples, float min_step)
{
  float dist = b - a;
  int min_step_samples = int(ceil(dist / min_step));
  return min(min_step_samples, max_samples);
}

float step_size(float a, float b, int num_steps)
{
  float dist = b - a;
  return dist / num_steps;
}

float next_point(float p, float step_size)
{
  return p + step_size;
}
