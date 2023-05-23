#version 410 core

uniform vec3 light_direction;
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float cloud_max_step;
uniform float opacity_step;
uniform float scatter_amount;
uniform float depth;
uniform float level_of_detail;

in VS_OUT
{
  vec3 origin;
} fs_in;

layout (location = 0) out float opacity_offset;
<% (doseq [i (range num-layers)] %>
layout (location = <%= (inc i) %>) out float opacity_layer_<%= i %>;
<% ) %>

vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
float cloud_density(vec3 point, float lod);
float sampling_offset();

void interpolate_opacity(float opacity_interval_begin, float opacity_interval_end, float previous_transmittance, float transmittance)
{
  float stepsize = opacity_interval_end - opacity_interval_begin;
  if (opacity_interval_begin == 0.0)
    opacity_layer_0 = 1.0;
<% (doseq [i (range 1 num-layers)] %>
  if (opacity_interval_begin < <%= i %> * opacity_step && opacity_interval_end >= <%= i %> * opacity_step) {
    opacity_layer_<%= i %> = mix(previous_transmittance, transmittance, (<%= i %> * opacity_step - opacity_interval_begin) / stepsize);
  };
<% ) %>
}

void main()
{
  vec4 intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, fs_in.origin, -light_direction);
  float previous_transmittance = 1.0;
  float start_depth = 0.0;
  float previous_opacity_depth = 0.0;
  float opacity_depth = 0.0;
  for (int segment=0; segment<2; segment++) {
    float start_segment = segment == 0 ? intersections.x : intersections.z;
    float extent_segment = segment == 0 ? intersections.y : intersections.w;
    if (extent_segment > 0) {
      int steps = int(ceil(extent_segment / cloud_max_step));
      float stepsize = extent_segment / steps;
      for (int i=0; i<steps; i++) {
        vec3 sample_point = fs_in.origin - (start_segment + (i + sampling_offset()) * stepsize) * light_direction;
        float density = cloud_density(sample_point, level_of_detail);
        // Compute this on the CPU: scatter_amount = (anisotropic * phase(0.76, -1) + 1 - anisotropic)
        float transmittance_step = exp((scatter_amount - 1) * density * stepsize);
        float transmittance = previous_transmittance * transmittance_step;
        if (previous_transmittance == 1.0) {
          start_depth = start_segment + i * stepsize;
        };
        if (transmittance < 1.0) {
          opacity_depth += stepsize;
          interpolate_opacity(previous_opacity_depth, opacity_depth, previous_transmittance, transmittance);
        };
        previous_transmittance = transmittance;
        previous_opacity_depth = opacity_depth;
      };
      if (segment == 0 && intersections.w > 0 && previous_transmittance < 1.0) {
        opacity_depth += intersections.z - intersections.x - intersections.y;
        interpolate_opacity(previous_opacity_depth, opacity_depth, previous_transmittance, previous_transmittance);
        previous_opacity_depth = opacity_depth;
      };
    };
    if (previous_opacity_depth > <%= num-layers %> * opacity_step)
      break;
  };
  opacity_depth = <%= num-layers %> * opacity_step;
  interpolate_opacity(previous_opacity_depth, opacity_depth, previous_transmittance, previous_transmittance);
  if (previous_transmittance == 1.0)
    start_depth = depth;
  opacity_offset = 1.0 - start_depth / depth;
}
