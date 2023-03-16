#version 410 core

uniform samplerCube <%= current-name %>;
uniform int size;

layout (location = 0) out float output1;
layout (location = 1) out float output2;
layout (location = 2) out float output3;
layout (location = 3) out float output4;
layout (location = 4) out float output5;
layout (location = 5) out float output6;

float <%= lookup-name %>(vec3 point);
vec3 convert_cubemap_index(vec3 idx, int size);
vec3 face1_vector(vec2 texcoord);
vec3 face2_vector(vec2 texcoord);
vec3 face3_vector(vec2 texcoord);
vec3 face4_vector(vec2 texcoord);
vec3 face5_vector(vec2 texcoord);
vec3 face6_vector(vec2 texcoord);

float lookup(vec3 v)
{
  vec3 adapted = convert_cubemap_index(v, size);
  vec3 warp_vector = texture(<%= current-name %>, adapted).xyz;
  return <%= lookup-name %>(normalize(warp_vector));
}

void main()
{
  vec2 x = (gl_FragCoord.xy - 0.5) / (size - 1);
  output1 = lookup(face1_vector(x));
  output2 = lookup(face2_vector(x));
  output3 = lookup(face3_vector(x));
  output4 = lookup(face4_vector(x));
  output5 = lookup(face5_vector(x));
  output6 = lookup(face6_vector(x));
}
