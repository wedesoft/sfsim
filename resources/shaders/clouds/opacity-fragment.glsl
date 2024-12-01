#version 410 core

uniform vec3 light_direction;
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
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
int number_of_samples(float a, float b, float max_step);
float step_size(float a, float b, int num_samples);
float sample_point(float a, float idx, float step_size);

void main()
{
  vec4 intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, fs_in.origin, -light_direction);
  float previous_transmittance = 1.0;
  float start_depth = 0.0;
  float start_segment = intersections.x;
  float extent_segment = intersections.y;
  int current_layer = 0;
  opacity_layer_0 = 1.0;
  if (extent_segment > 0) {
    int steps = number_of_samples(start_segment, start_segment + extent_segment, opacity_step);
    float stepsize = step_size(start_segment, start_segment + extent_segment, steps);
    for (int i=0; i<steps; i++) {
      float s = sample_point(start_segment, i, stepsize);
      vec3 sample_point = fs_in.origin - s * light_direction;
      float density = cloud_density(sample_point, level_of_detail);
      float transmittance;
      // Compute this on the CPU: scatter_amount = (anisotropic * phase(0.76, -1) + 1 - anisotropic)
      if (density > 0.0) {
        float transmittance_step = exp((scatter_amount - 1) * density * stepsize);
        transmittance = previous_transmittance * transmittance_step;
      } else
        transmittance = previous_transmittance;
      if (previous_transmittance == 1.0) {
        start_depth = start_segment + i * stepsize;
      };
      if (transmittance < 1.0) {
        current_layer = current_layer + 1;
        switch (current_layer) {
<% (doseq [i (range 1 num-layers)] %>
          case <%= i %>:
            opacity_layer_<%= i %> = transmittance;
<% ) %>
        };
      };
      previous_transmittance = transmittance;
      if (current_layer >= <%= (dec num-layers ) %>)
        break;
    };
  };
  if (previous_transmittance == 1.0)
    start_depth = depth;
  opacity_offset = 1.0 - start_depth / depth;
}
