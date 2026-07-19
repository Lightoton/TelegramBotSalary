# Telegram Bot Salary Tracker

[🇷🇺 Читать на русском](#telegram-bot-salary-tracker-русский)

A Telegram bot designed to track income for freelancers and service providers. The project focuses on flexibility, ease of use, and automatic calculation of taxes and personal expenses (rent, food).

## 🌟 Features
- **Dynamic Categories:** Create, rename, and delete services with custom commission percentages (e.g., "Laser Hair Removal", "Massage").
- **Hourly Rate:** Built-in support for time-based work. Just input your shift as `13:00-15:30` and the bot calculates the rest.
- **Smart Statistics:** View your earnings calendar and automatic profit calculation for the current month.
- **Month Closing:** Unique archiving system. Closed months are saved in "History" and protected from accidental edits.
- **"For Life" Budgeting:** The bot subtracts your predefined monthly expenses (Rent, Food, Taxes, Extra) from your earnings and shows your free money.

---

## 🛠 Tech Stack
- **Language:** Java 21
- **Framework:** Spring Boot 3
- **Database:** PostgreSQL (production) / H2 (local development and tests)
- **Telegram API:** `telegrambots-spring-boot-starter`
- **Infrastructure:** Docker & Docker Compose

---

## 🚀 Setup & Deployment

To deploy the project, you need **Docker** and **Docker Compose** installed.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Lightoton/TelegramBotSalary.git
   cd TelegramBotSalary
   ```

2. **Set up environment variables:**
   Copy the `.env.example` file to `.env` in the root of the project:
   ```bash
   cp .env.example .env
   ```
   Open `.env` and enter your Telegram bot token (get it from @BotFather):
   ```env
   TELEGRAM_BOT_TOKEN=your_bot_token_here
   TELEGRAM_BOT_USERNAME=your_bot_username_here
   bot.admin.password=your_secret_password_here
   ```

3. **Start the containers:**
   ```bash
   docker-compose up --build -d
   ```
   The bot will automatically build, the PostgreSQL database will start, and the application will connect to Telegram.

---

## 📖 User Guide

### Initial Setup (Onboarding)
When you start the bot for the first time by sending `/start`, it requires a secret setup process to ensure only authorized users (the bot owner) can access it:
1. **Secret Password:** The bot will ask for an admin password. This password is set in your configuration properties (`bot.admin.password`). Once you enter it correctly, your Telegram account is granted access.
2. **Hourly Rate:** You will be prompted to enter your default hourly rate (e.g., `15.50` or `15,50` — you can use either a dot or a comma for cents).
3. **Monthly Tax:** You will be prompted to enter your fixed monthly tax amount.
4. **Services Setup:** You will be asked to add your services one by one. For each service, you type the name (e.g., "Laser Hair Removal") and then the percentage you earn from it (e.g., `40`). You can continue adding services until you press the **"✅ Завершить" (Finish)** button.

After completing these steps, the main menu will appear.

### The Main Menu
The bot has 6 main buttons at the bottom of the screen.

#### 1. 💰 Добавить доход (Add Income)
Use this button every time you want to record earned money.
- **Select Date:** Choose "Today", "Yesterday", or "Another day" via the inline calendar.
- **Select Service:** The bot will suggest your saved services or a standard "Почасовая ставка" (Hourly payment) button.
- **Input Amount:**
  - If a *service* is selected, enter the **full cost** of the service paid by the client (e.g., `650.10` or `650,10`). The bot will automatically calculate your share based on the set percentage.
  - If *hourly payment* is selected, enter the shift interval in the format `13:00-15:30`. The bot will calculate the hours worked and multiply by your hourly rate.

#### 2. 📊 Статистика (Statistics)
- Displays a prompt to select a day.
- You can select a specific date from the calendar to view all transactions recorded on that day.
- If you made a mistake, you can click on an open transaction on that day to **Edit** the recorded amount.

#### 3. 🔒 Закрыть месяц (Close Month)
- When the month ends, press this button. The bot will find all open months with transactions and suggest closing them.
- After confirmation, the month is **archived**. Its transactions can no longer be edited (a 🔒 icon will appear instead of the edit options). This protects your past financial data from accidental changes.

#### 4. 📁 История (History)
- Displays all your closed and archived months, showing your total earnings for each of those months.

#### 5. 🏠 На жизнь (For Life / Expenses)
This is a smart budget calculator where you track your personal expenses.
- **Initial Setup:** The very first time you click this button, the bot will ask you to set up your fixed monthly expense limits step-by-step:
  1. **Rent** (Аренда)
  2. **Food** (Еда)
  3. **Extra** (Дополнительные расходы)
- **Daily Usage:** Once set up, clicking this button takes your net profit for the current month and subtracts your predefined "Taxes", "Rent", "Food", and "Extra". Finally, it shows if you covered your basic needs or if you are in the green (Free money).
- You can change these limits at any time by clicking the inline buttons that appear along with the budget report.

#### 6. ⚙️ Настройки (Settings)
Configure your basic calculation logic here.
- **Управление услугами (Manage Services):** Add new services with their %, rename existing ones, or delete them.
- **Изменить ставку в час (Change hourly rate):** Update your default hourly rate.
- **Изменить налог в месяц (Change monthly tax):** Update your fixed monthly tax deduction.

---

## 📄 License
This project is licensed under the **MIT License**. See the `LICENSE` file for details.

<br><br>

---

# Telegram Bot Salary Tracker (Русский)

Бот для учета самозанятых доходов (фриланс, услуги) в Telegram. Проект создан с фокусом на гибкость, удобство использования и автоматическое отслеживание налогов и личных расходов (аренда, питание).

## 🌟 Особенности
- **Динамические категории:** Вы можете создавать, переименовывать и удалять услуги со своими процентами комиссии (например, "Лазерная эпиляция", "Массаж").
- **Почасовая ставка:** Для работы с оплатой за время предусмотрен расчет по расписанию (ввод смен вида `13:00-15:30`).
- **Умная статистика:** Календарь доходов по дням и автоматический расчет прибыли за текущий месяц.
- **Закрытие месяцев:** Уникальная система архивации. Закрытые месяцы сохраняются в "Истории" и защищаются от случайного редактирования.
- **Расчет бюджета "На жизнь":** Бот вычитает заданные вами ежемесячные расходы (Аренда, Еда, Налоги, Доп. траты) из заработанного и показывает свободные деньги.

---

## 🛠 Технический стек
- **Язык:** Java 21
- **Фреймворк:** Spring Boot 3
- **БД:** PostgreSQL (в продакшене) / H2 (для локальной разработки и тестов)
- **Telegram API:** `telegrambots-spring-boot-starter`
- **Инфраструктура:** Docker & Docker Compose

---

## 🚀 Установка и Запуск (Deploy)

Для развертывания проекта вам потребуется установленный **Docker** и **Docker Compose**.

1. **Склонируйте репозиторий:**
   ```bash
   git clone https://github.com/Lightoton/TelegramBotSalary.git
   cd TelegramBotSalary
   ```

2. **Настройте переменные окружения:**
   В корне проекта скопируйте файл `.env.example` в `.env`:
   ```bash
   cp .env.example .env
   ```
   Откройте `.env` и впишите ваш токен Telegram бота (получить можно у @BotFather):
   ```env
   TELEGRAM_BOT_TOKEN=your_bot_token_here
   TELEGRAM_BOT_USERNAME=your_bot_username_here
   bot.admin.password=your_secret_password_here
   ```

3. **Запустите контейнеры:**
   ```bash
   docker-compose up --build -d
   ```
   Бот автоматически соберется, запустится база данных PostgreSQL, и приложение подключится к Telegram.

---

## 📖 Руководство пользователя

### Первоначальная настройка (Онбординг)
Когда вы впервые запускаете бота командой `/start`, он запрашивает секретный пароль, чтобы убедиться, что только хозяин имеет к нему доступ:
1. **Секретный пароль:** Бот попросит ввести пароль администратора (задается в файле конфигурации или `.env` как `bot.admin.password`). После правильного ввода вашему аккаунту выдается доступ.
2. **Ставка в час:** Бот попросит ввести стоимость вашего часа работы. Вы можете вводить копейки/центы через точку или запятую (например, `15.50` или `15,50`).
3. **Налог в месяц:** Укажите фиксированную сумму ежемесячного налога.
4. **Настройка услуг:** Бот предложит добавить ваши основные услуги. Вводите название услуги (например, "Лазер"), а затем процент, который вы с нее получаете (например, `40`). Продолжайте добавлять услуги, пока не нажмете кнопку **"✅ Завершить"**.

После этого перед вами появится главное меню бота с 6 кнопками.

### Главное меню

#### 1. 💰 Добавить доход
Эта кнопка используется каждый раз, когда вы хотите записать заработанные деньги.
- **Выбор даты:** Можно выбрать "Сегодня", "Вчера" или "Другой день" через календарь.
- **Выбор услуги:** Бот предложит ваши сохраненные услуги или стандартную кнопку "Почасовая оплата".
- **Ввод суммы:**
  - Если выбрана *услуга*, введите **полную сумму** (например, `650.10` или `650,10`), которую заплатил клиент. Бот сам высчитает вашу долю по заданному проценту. Можно использовать как точку, так и запятую для центов.
  - Если выбрана *почасовая оплата*, введите интервал смены в формате `13:00-15:30`. Бот сам посчитает часы и умножит их на вашу почасовую ставку.

#### 2. 📊 Статистика
- Показывает выбор дня для детальной статистики.
- Через "Выбрать дату" открывается календарь. Выбирая день, вы видите все добавленные записи за эту дату.
- Если вы ошиблись при добавлении дохода, просто нажмите на него в статистике и выберите **Отредактировать**, чтобы ввести верную сумму.

#### 3. 🔒 Закрыть месяц
- Когда месяц заканчивается, вы нажимаете эту кнопку. Бот найдет все открытые месяцы с записями и предложит их закрыть.
- После подтверждения месяц **архивируется**. Доходы за этот месяц больше нельзя редактировать (в статистике вместо кнопки редактирования появится значок 🔒). Это защищает прошлые расчеты от случайных изменений.

#### 4. 📁 История
- Показывает все ваши закрытые (архивные) месяцы и итоговую заработанную сумму за каждый из них.

#### 5. 🏠 На жизнь (Расходы)
Это умный калькулятор бюджета, который вычитает ваши нужды из заработанного.
- **Первая настройка:** При первом нажатии на эту кнопку бот попросит вас задать базовые лимиты расходов на месяц:
  1. **Аренда**
  2. **Еда**
  3. **Дополнительные расходы (Допы)**
- **Ежедневное использование:** При нажатии кнопки бот берет вашу чистую прибыль за текущий месяц и вычитает из нее ваши лимиты ("Налог", "Аренду", "Еду" и "Допы"). В конце он показывает, покрыли ли вы базовые нужды (Не хватает N евро) или уже вышли в плюс (Свободные деньги).
- Под отчетом появляются кнопки ("Аренда", "Еда", "Допы"), с помощью которых можно в любой момент изменить лимиты.

#### 6. ⚙️ Настройки
Здесь вы настраиваете базовую логику расчетов (за исключением расходов, которые находятся в кнопке "На жизнь").
- **Управление услугами:** Здесь можно добавить новые услуги со своим %, переименовать текущие или удалить ненужные.
- **Изменить ставку в час:** Поменять вашу почасовую оплату.
- **Изменить налог в месяц:** Поменять фиксированный ежемесячный налог.

---

## 📄 Лицензия
Этот проект распространяется под **MIT License**. См. файл `LICENSE` для подробностей.
