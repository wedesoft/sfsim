#version 410 core

int number_of_steps(float a, float b, int max_samples, float max_step)
{
  return min(int(ceil(log(b / a) / log(max_step))), max_samples);
}

float scaling_offset(float a, float b, int samples, float max_step)
{
  float final_scale = pow(max_step, samples);
  return max(0, (b - a * final_scale) / (final_scale - 1));
}

float step_size(float a, float b, float scaling_offset, int num_steps)
{
  return pow((b + scaling_offset) / (a + scaling_offset), 1.0 / num_steps);
}

float next_point(float p, float scaling_offset, float step_size)
{
  return (p + scaling_offset) * step_size - scaling_offset;
}
