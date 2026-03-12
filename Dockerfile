# Use lightweight nginx to serve static files
FROM nginx:alpine

# Copy all static files into nginx's default serve directory
COPY . /usr/share/nginx/html

# Expose port 80
EXPOSE 80