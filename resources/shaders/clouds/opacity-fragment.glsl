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
    float s = start_segment;
    vec3 sample_point = fs_in.origin - start_segment * light_direction;
    vec3 light_step = opacity_step * light_direction;
    while (s < start_segment + extent_segment) {
      float density = cloud_density(sample_point, level_of_detail);
      float transmittance;
      // Compute this on the CPU: scatter_amount = (anisotropic * phase(0.76, -1) + 1 - anisotropic)
      if (density > 0.0) {
        float transmittance_step = exp((scatter_amount - 1) * density * opacity_step);
        transmittance = previous_transmittance * transmittance_step;
      } else
        transmittance = previous_transmittance;
      if (previous_transmittance == 1.0) {
        start_depth = s;
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
      s += opacity_step;
      sample_point -= light_step;
    };
  };
  if (previous_transmittance == 1.0)
    start_depth = depth;
  opacity_offset = 1.0 - start_depth / depth;
}
