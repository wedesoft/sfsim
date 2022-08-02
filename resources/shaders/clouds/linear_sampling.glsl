#version 410 core

int number_of_steps(vec3 origin, vec3 p, vec3 q, int max_samples, float min_step)
{
  float dist = distance(p, q);
  int min_step_samples = int(ceil(dist / min_step));
  return min(min_step_samples, max_samples);
}

float step_size(vec3 origin, vec3 p, vec3 q, int num_steps)
{
  float dist = distance(p, q);
  return dist / num_steps;
}

vec3 next_point(vec3 origin, vec3 point, float step_size)
{
  return point + normalize(point - origin) * step_size;
}
