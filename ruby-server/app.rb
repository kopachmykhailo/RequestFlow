require "sinatra"
require "sinatra/json"
require "json"

set :port, 4567
set :bind, "0.0.0.0"

DATA_FILE =
  if File.file?("app.rb")
    "data.json"
  elsif File.file?(File.join("ruby-server", "app.rb"))
    File.join("ruby-server", "data.json")
  else
    "data.json"
  end

def load_data
  unless File.exist?(DATA_FILE)
    return {
      "users" => [],
      "applications" => []
    }
  end

  begin
    data = JSON.parse(File.read(DATA_FILE))

    {
      "users" => data["users"] || [],
      "applications" => data["applications"] || []
    }
  rescue JSON::ParserError
    {
      "users" => [],
      "applications" => []
    }
  end
end

def save_data(data)
  File.write(
    DATA_FILE,
    JSON.pretty_generate(data)
  )
end

data = load_data

def ensure_admin_user!(data)
  admin_email = "admin1"
  admin_user = data["users"].find { |item| item["email"].to_s.downcase == admin_email }

  if admin_user.nil?
    next_id = data["users"].empty? ? 1 : data["users"].map { |item| item["id"].to_i }.max + 1
    admin_user = {
      "id" => next_id,
      "firebase_uid" => "admin-firebase-#{admin_email}",
      "name" => "Адміністратор",
      "email" => admin_email,
      "role" => "admin",
      "password" => admin_email
    }
    data["users"] << admin_user
    File.write(DATA_FILE, JSON.pretty_generate(data))
  else
    admin_user["role"] = "admin"
    admin_user["password"] = admin_email if admin_user["password"].nil? || admin_user["password"].to_s.empty?
    admin_user["email"] = admin_email.downcase
    File.write(DATA_FILE, JSON.pretty_generate(data))
  end
end

ensure_admin_user!(data)

if data["users"].any? { |user| user.key?("password") }
  data["users"].each { |user| user.delete("password") }
  save_data(data)
end

before do
  content_type :json
end


# =========================
# ГОЛОВНА
# =========================

get "/" do
  json(
    application: "RequestFlow",
    status: "running"
  )
end


# =========================
# ПЕРЕВІРКА API
# =========================

get "/api/health" do
  json(
    status: "ok",
    message: "RequestFlow API працює"
  )
end


# =========================
# СИНХРОНІЗАЦІЯ ПРОФІЛЮ FIREBASE
# =========================

post "/api/auth/sync" do

  begin
    request_data = JSON.parse(request.body.read)
  rescue JSON::ParserError
    status 400

    return json(
      error: "Некоректний JSON"
    )
  end

  name = request_data["name"]
  email = request_data["email"]
  firebase_uid = request_data["firebase_uid"]

  if firebase_uid.nil? || firebase_uid.strip.empty?
    status 400

    return json(
      error: "Firebase UID є обов'язковим"
    )
  end

  if email.nil? || email.strip.empty?
    status 400

    return json(
      error: "Email є обов'язковим"
    )
  end

  normalized_email = email.strip.downcase
  clean_name = name.to_s.strip
  user = data["users"].find do |item|
    item["firebase_uid"] == firebase_uid ||
      item["email"].to_s.downcase == normalized_email
  end

  if user
    user["firebase_uid"] = firebase_uid
    user["name"] = clean_name unless clean_name.empty?
    user["email"] = normalized_email
    user.delete("password")
    status 200
  else
    next_id = data["users"].empty? ? 1 : data["users"].map { |item| item["id"].to_i }.max + 1
    user = {
      "id" => next_id,
      "firebase_uid" => firebase_uid,
      "name" => clean_name.empty? ? normalized_email.split("@").first : clean_name,
      "email" => normalized_email
    }
    data["users"] << user
    status 201
  end

  save_data(data)

  user["role"] = "user" if user["role"].nil?

  json(
    message: "Профіль синхронізовано",
    user: {
      id: user["id"],
      name: user["name"],
      email: user["email"],
      role: user["role"]
    }
  )
end

# =========================
# ВХІД АДМІНІСТРАТОРА
# =========================

post "/api/auth/admin-login" do
  begin
    request_data = JSON.parse(request.body.read)
  rescue JSON::ParserError
    status 400
    return json(error: "Некоректний JSON")
  end

  email = request_data["email"].to_s.strip.downcase
  password = request_data["password"].to_s

  if email != "admin1" || password != "admin1"
    status 401
    return json(error: "Невірні дані адміністратора")
  end

  admin_user = data["users"].find { |item| item["email"].to_s.downcase == "admin1" }
  unless admin_user
    status 404
    return json(error: "Адміністратор не знайдений")
  end

  admin_user["role"] = "admin"

  json(
    message: "Адмін успішно увійшов",
    user: {
      id: admin_user["id"],
      name: admin_user["name"],
      email: admin_user["email"],
      role: "admin"
    }
  )
end


# =========================
# ОТРИМАННЯ ЗАЯВОК
# =========================

get "/api/applications" do

  user_id = params["user_id"]
  firebase_uid = params["firebase_uid"]

  if user_id && !user_id.to_s.strip.empty?
    current_user = data["users"].find do |item|
      item["id"].to_s == user_id.to_s ||
        (firebase_uid && item["firebase_uid"].to_s == firebase_uid.to_s)
    end
  elsif firebase_uid && !firebase_uid.to_s.strip.empty?
    current_user = data["users"].find { |item| item["firebase_uid"].to_s == firebase_uid.to_s }
  else
    current_user = nil
  end

  if current_user && current_user["role"].to_s == "admin"
    json(applications: data["applications"])
  elsif current_user
    user_applications = data["applications"].select do |application|
      application["user_id"].to_s == current_user["id"].to_s ||
        application["firebase_uid"].to_s == current_user["firebase_uid"].to_s
    end
    json(applications: user_applications)
  else
    json(applications: [])
  end
end


# =========================
# СТВОРЕННЯ ЗАЯВКИ
# =========================

post "/api/applications" do

  begin
    request_data = JSON.parse(request.body.read)
  rescue JSON::ParserError

    status 400

    return json(
      error: "Некоректний JSON"
    )
  end

  user_id = request_data["user_id"]
  firebase_uid = request_data["firebase_uid"]
  title = request_data["title"]
  category = request_data["category"]
  priority = request_data["priority"]
  description = request_data["description"]


  # =========================
  # ПЕРЕВІРКА КОРИСТУВАЧА
  # =========================

  if user_id.nil? && (firebase_uid.nil? || firebase_uid.to_s.strip.empty?)

    status 400

    return json(
      error: "Користувач є обов'язковим"
    )
  end

  user = data["users"].find do |item|
    (user_id && item["id"].to_s == user_id.to_s) ||
      (firebase_uid && item["firebase_uid"].to_s == firebase_uid.to_s)
  end

  unless user

    status 404

    return json(
      error: "Користувача не знайдено"
    )
  end

  if user["role"].to_s == "admin"
    status 403

    return json(
      error: "Адміністратор не може створювати заявки"
    )
  end


  # =========================
  # ПЕРЕВІРКИ ЗАЯВКИ
  # =========================

  if title.nil? || title.strip.empty?

    status 400

    return json(
      error: "Тема заявки є обов'язковою"
    )
  end

  if category.nil? || category.strip.empty?

    status 400

    return json(
      error: "Категорія є обов'язковою"
    )
  end

  if priority.nil? || priority.strip.empty?

    status 400

    return json(
      error: "Пріоритет є обов'язковим"
    )
  end

  if description.nil? || description.strip.empty?

    status 400

    return json(
      error: "Опис заявки є обов'язковим"
    )
  end


  # =========================
  # СТВОРЕННЯ ID
  # =========================

  next_id =
    if data["applications"].empty?
      1
    else
      data["applications"]
        .map { |application| application["id"].to_i }
        .max + 1
    end


  # =========================
  # СТВОРЕННЯ ЗАЯВКИ
  # =========================

  application = {
    "id" => next_id,
    "user_id" => user["id"],
    "firebase_uid" => user["firebase_uid"],
    "user_name" => user["name"],
    "title" => title.strip,
    "category" => category.strip,
    "priority" => priority.strip,
    "description" => description.strip,
    "status" => "Нова",
    "admin_response" => ""
  }


  data["applications"] << application

  save_data(data)

  status 201

  json(
    message: "Заявку успішно створено",
    application: application
  )
end

# =========================
# ОНОВЛЕННЯ СТАТУСУ ТА ВІДПОВІДІ АДМІНА
# =========================

patch "/api/applications/:id" do
  begin
    request_data = JSON.parse(request.body.read)
  rescue JSON::ParserError
    status 400
    return json(error: "Некоректний JSON")
  end

  application_id = params[:id].to_i
  application = data["applications"].find { |item| item["id"].to_i == application_id }

  if application.nil?
    status 404
    return json(error: "Заявку не знайдено")
  end

  user_id = request_data["user_id"]
  firebase_uid = request_data["firebase_uid"]
  admin_user = data["users"].find do |item|
    (user_id && item["id"].to_s == user_id.to_s) ||
      (firebase_uid && item["firebase_uid"].to_s == firebase_uid.to_s)
  end

  unless admin_user && admin_user["role"].to_s == "admin"
    status 403
    return json(error: "Тільки адміністратор може змінювати статус заявки")
  end

  if request_data.key?("status") && !request_data["status"].to_s.strip.empty?
    application["status"] = request_data["status"].strip
  end

  if request_data.key?("admin_response")
    application["admin_response"] = request_data["admin_response"].to_s.strip
  end

  save_data(data)

  json(
    message: "Статус і відповідь оновлено",
    application: application
  )
end