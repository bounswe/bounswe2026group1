# Use lightweight nginx to serve static files
FROM nginx:alpine

RUN rm -rf /usr/share/nginx/html/*

# Copy all static files into nginx's default serve directory
COPY . /usr/share/nginx/html

# Expose port 80
EXPOSE 80