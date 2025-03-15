#version 410 core

uniform vec3 light_direction;
uniform float cloud_step;
uniform float lod_offset;
uniform float anisotropic;
uniform float opacity_cutoff;

float sampling_offset();
float sample_point(float a, float idx, float step_size);
float phase(float g, float mu);
float lod_at_distance(float dist, float lod_offset);
float cloud_density(vec3 point, float lod);
vec4 cloud_transfer(vec3 start, vec3 point, float scatter_amount, float stepsize, vec4 cloud_scatter, float density);

vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter)
{
  float noise = sampling_offset();
  float scatter_amount = anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic;
  float l = cloud_shell.x + noise * cloud_step;
  while (l < cloud_shell.x + cloud_shell.y) {
    if (cloud_scatter.a <= opacity_cutoff)
      break;
    vec3 point = origin + l * direction;
    float lod = lod_at_distance(l, lod_offset);
    float density = cloud_density(point, lod);
    if (density > 0)
      cloud_scatter = cloud_transfer(start, point, scatter_amount, cloud_step, cloud_scatter, density);
    l += cloud_step;
  };
  return cloud_scatter;
}
